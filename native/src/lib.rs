//! Native inference bridge for Dictator's on-device STT prototype.
//!
//! The implementation follows Kyutai's standalone Rust STT example. It keeps
//! the model loaded between sessions, feeds 24 kHz mono PCM to Mimi in 1,920
//! sample blocks, and decodes text through `moshi::asr::State`.

use std::path::{Path, PathBuf};
use std::sync::Mutex;

use anyhow::{Context, Result};
use candle_core::{Device, Tensor};
use candle_transformers::quantized_var_builder::VarBuilder as QuantizedVarBuilder;
use jni::JNIEnv;
use jni::objects::{JClass, JFloatArray, JString};
use jni::sys::{jlong, jstring};
use sentencepiece::SentencePieceProcessor;
use serde::Deserialize;

const PCM_SAMPLE_RATE_HZ: usize = 24_000;
const PCM_FRAME_SAMPLES: usize = 1_920;
const MODEL_FILE: &str = "model.q8_0.gguf";
const MIMI_FILE: &str = "mimi-pytorch-e351c8d8@125.safetensors";
const TOKENIZER_FILE: &str = "tokenizer_en_fr_audio_8000.model";
const CONFIG_FILE: &str = "config.json";

#[derive(Debug, Deserialize)]
struct SttConfig {
    audio_silence_prefix_seconds: f64,
    audio_delay_seconds: f64,
}

#[derive(Debug, Deserialize)]
struct Config {
    card: usize,
    text_card: usize,
    dim: usize,
    n_q: usize,
    context: usize,
    max_period: f64,
    num_heads: usize,
    num_layers: usize,
    causal: bool,
    stt_config: SttConfig,
}

impl Config {
    fn model_config(&self) -> moshi::lm::Config {
        let transformer = moshi::transformer::Config {
            d_model: self.dim,
            num_heads: self.num_heads,
            num_layers: self.num_layers,
            dim_feedforward: self.dim * 4,
            causal: self.causal,
            norm_first: true,
            bias_ff: false,
            bias_attn: false,
            layer_scale: None,
            positional_embedding: moshi::transformer::PositionalEmbedding::Rope,
            use_conv_block: false,
            cross_attention: None,
            conv_kernel_size: 3,
            use_conv_bias: false,
            gating: Some(candle_nn::Activation::Silu),
            norm: moshi::NormType::RmsNorm,
            context: self.context,
            max_period: self.max_period as usize,
            conv_layout: false,
            kv_repeat: 1,
            max_seq_len: 4096 * 4,
            shared_cross_attn: false,
        };
        moshi::lm::Config {
            transformer,
            depformer: None,
            audio_vocab_size: self.card + 1,
            text_in_vocab_size: self.text_card + 1,
            text_out_vocab_size: self.text_card,
            audio_codebooks: self.n_q,
            conditioners: Default::default(),
            extra_heads: None,
        }
    }
}

struct NativeStt {
    state: moshi::asr::State,
    tokenizer: SentencePieceProcessor,
    config: Config,
    device: Device,
    pending_pcm: Vec<f32>,
    transcript: String,
}

impl NativeStt {
    fn load(model_directory: &Path) -> Result<Self> {
        let config_path = required_file(model_directory, CONFIG_FILE)?;
        let config: Config = serde_json::from_slice(
            &std::fs::read(&config_path)
                .with_context(|| format!("could not read {}", config_path.display()))?,
        )
        .context("could not parse STT config")?;
        let model_path = required_file(model_directory, MODEL_FILE)?;
        let mimi_path = required_file(model_directory, MIMI_FILE)?;
        let tokenizer_path = required_file(model_directory, TOKENIZER_FILE)?;

        let device = Device::Cpu;
        let tokenizer = SentencePieceProcessor::open(&tokenizer_path)
            .context("could not load the STT tokenizer")?;
        let weights = QuantizedVarBuilder::from_gguf(&model_path, &device)
            .context("could not load Q4 STT weights")?;
        let language_model = moshi::lm::LmModel::new(
            &config.model_config(),
            moshi::nn::MaybeQuantizedVarBuilder::Quantized(weights),
        )
        .context("could not initialize the STT transformer")?;
        let mimi = moshi::mimi::load(
            mimi_path
                .to_str()
                .context("Mimi model path is not valid UTF-8")?,
            Some(32),
            &device,
        )
        .context("could not load Mimi")?;
        let delay_tokens = (config.stt_config.audio_delay_seconds * 12.5) as usize;
        let state = moshi::asr::State::new(1, delay_tokens, 0.0, mimi, language_model)
            .context("could not initialize the streaming STT state")?;

        Ok(Self {
            state,
            tokenizer,
            config,
            device,
            pending_pcm: Vec::with_capacity(PCM_FRAME_SAMPLES),
            transcript: String::new(),
        })
    }

    fn start(&mut self) -> Result<()> {
        self.state
            .reset()
            .context("could not reset the STT state")?;
        self.pending_pcm.clear();
        self.transcript.clear();
        let prefix_samples = (self.config.stt_config.audio_silence_prefix_seconds
            * PCM_SAMPLE_RATE_HZ as f64) as usize;
        self.push_pcm(&vec![0.0; prefix_samples])
    }

    fn feed(&mut self, pcm: &[f32]) -> Result<String> {
        self.push_pcm(pcm)?;
        Ok(self.transcript.clone())
    }

    fn finish(&mut self) -> Result<String> {
        let suffix_samples = (self.config.stt_config.audio_delay_seconds
            * PCM_SAMPLE_RATE_HZ as f64) as usize
            + PCM_SAMPLE_RATE_HZ;
        self.push_pcm(&vec![0.0; suffix_samples])?;
        if !self.pending_pcm.is_empty() {
            let padding = PCM_FRAME_SAMPLES - self.pending_pcm.len();
            self.pending_pcm.extend(std::iter::repeat_n(0.0, padding));
            self.process_pending_frame()?;
        }
        Ok(self.transcript.trim().to_owned())
    }

    fn push_pcm(&mut self, pcm: &[f32]) -> Result<()> {
        self.pending_pcm.extend_from_slice(pcm);
        while self.pending_pcm.len() >= PCM_FRAME_SAMPLES {
            self.process_pending_frame()?;
        }
        Ok(())
    }

    fn process_pending_frame(&mut self) -> Result<()> {
        let frame: Vec<f32> = self.pending_pcm.drain(..PCM_FRAME_SAMPLES).collect();
        let pcm = Tensor::new(frame, &self.device)?.reshape((1, 1, ()))?;
        let messages = self.state.step_pcm(pcm, None, &().into(), |_, _, _| ())?;
        for message in messages {
            if let moshi::asr::AsrMsg::Word { tokens, .. } = message {
                let word = self.tokenizer.decode_piece_ids(&tokens).unwrap_or_default();
                if !word.is_empty() {
                    self.transcript.push(' ');
                    self.transcript.push_str(&word);
                }
            }
        }
        Ok(())
    }
}

/// Loads the pinned Q4 bundle without running inference. This is used only to
/// reject incompatible model conversions before they reach the phone.
pub fn verify_model_bundle(directory: &Path) -> Result<()> {
    NativeStt::load(directory).map(|_| ())
}

/// Runs a local fixture through the same stream loop as Android. This is a
/// conversion check only, never a desktop performance benchmark.
pub fn transcribe_fixture(directory: &Path, pcm_24k_mono: &[f32]) -> Result<String> {
    let mut engine = NativeStt::load(directory)?;
    engine.start()?;
    engine.feed(pcm_24k_mono)?;
    engine.finish()
}

fn required_file(directory: &Path, name: &str) -> Result<PathBuf> {
    let path = directory.join(name);
    if path.is_file() {
        Ok(path)
    } else {
        anyhow::bail!("missing model asset: {}", path.display())
    }
}

unsafe fn engine_from_handle(handle: jlong) -> Result<&'static Mutex<NativeStt>> {
    if handle == 0 {
        anyhow::bail!("the native STT engine is closed")
    }
    Ok(unsafe { &*(handle as *const Mutex<NativeStt>) })
}

fn throw(env: &mut JNIEnv<'_>, error: impl std::fmt::Display) {
    let _ = env.throw_new("java/lang/IllegalStateException", error.to_string());
}

fn model_directory(env: &mut JNIEnv<'_>, value: JString<'_>) -> Result<PathBuf> {
    Ok(PathBuf::from(
        env.get_string(&value)
            .context("could not read model directory from Java")?
            .to_string_lossy()
            .into_owned(),
    ))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_jyri_dictator_speech_NativeSttBridge_nativeCreate(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    directory: JString<'_>,
) -> jlong {
    match model_directory(&mut env, directory).and_then(|directory| NativeStt::load(&directory)) {
        Ok(engine) => Box::into_raw(Box::new(Mutex::new(engine))) as jlong,
        Err(error) => {
            throw(&mut env, error);
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_jyri_dictator_speech_NativeSttBridge_nativeStart(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    let result = unsafe { engine_from_handle(handle) }.and_then(|engine| {
        engine
            .lock()
            .map_err(|_| anyhow::anyhow!("native STT engine lock is poisoned"))?
            .start()
    });
    if let Err(error) = result {
        throw(&mut env, error);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_jyri_dictator_speech_NativeSttBridge_nativeFeed(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    pcm: JFloatArray<'_>,
) -> jstring {
    let result = (|| -> Result<String> {
        let length = env.get_array_length(&pcm)? as usize;
        let mut samples = vec![0.0; length];
        env.get_float_array_region(&pcm, 0, &mut samples)?;
        let engine = unsafe { engine_from_handle(handle) }?;
        engine
            .lock()
            .map_err(|_| anyhow::anyhow!("native STT engine lock is poisoned"))?
            .feed(&samples)
    })();
    match result {
        Ok(transcript) => env
            .new_string(transcript)
            .map_or(std::ptr::null_mut(), |value| value.into_raw()),
        Err(error) => {
            throw(&mut env, error);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_jyri_dictator_speech_NativeSttBridge_nativeFinish(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) -> jstring {
    let result = unsafe { engine_from_handle(handle) }.and_then(|engine| {
        engine
            .lock()
            .map_err(|_| anyhow::anyhow!("native STT engine lock is poisoned"))?
            .finish()
    });
    match result {
        Ok(transcript) => env
            .new_string(transcript)
            .map_or(std::ptr::null_mut(), |value| value.into_raw()),
        Err(error) => {
            throw(&mut env, error);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_jyri_dictator_speech_NativeSttBridge_nativeClose(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            drop(Box::from_raw(handle as *mut Mutex<NativeStt>));
        }
    }
}
