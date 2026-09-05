use std::path::PathBuf;

fn main() -> anyhow::Result<()> {
    let mut args = std::env::args().skip(1);
    let model_directory = args.next().map(PathBuf::from).ok_or_else(|| {
        anyhow::anyhow!("usage: transcribe_fixture <model-directory> <pcm-f32le-file>")
    })?;
    let pcm_path = args
        .next()
        .map(PathBuf::from)
        .ok_or_else(|| anyhow::anyhow!("missing PCM fixture"))?;
    let bytes = std::fs::read(pcm_path)?;
    if bytes.len() % std::mem::size_of::<f32>() != 0 {
        anyhow::bail!("PCM fixture must be 32-bit float little-endian samples")
    }
    let pcm = bytes
        .chunks_exact(std::mem::size_of::<f32>())
        .map(|chunk| f32::from_le_bytes(chunk.try_into().expect("f32-sized chunk")))
        .collect::<Vec<_>>();
    println!(
        "{}",
        dictator_stt::transcribe_fixture(&model_directory, &pcm)?
    );
    Ok(())
}
