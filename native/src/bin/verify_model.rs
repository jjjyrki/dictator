use std::path::PathBuf;

fn main() -> anyhow::Result<()> {
    let directory = std::env::args()
        .nth(1)
        .map(PathBuf::from)
        .ok_or_else(|| anyhow::anyhow!("usage: verify_model <model-directory>"))?;
    dictator_stt::verify_model_bundle(&directory)?;
    println!("Model bundle loaded successfully.");
    Ok(())
}
