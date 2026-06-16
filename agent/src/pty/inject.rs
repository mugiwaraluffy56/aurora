use tokio::io::AsyncWrite;
use tokio::io::AsyncWriteExt;

use crate::error::Result;

pub async fn inject_yes<W: AsyncWrite + Unpin>(master: &mut W) -> Result<()> {
    master.write_all(b"y\n").await?;
    Ok(())
}

pub async fn inject_no<W: AsyncWrite + Unpin>(master: &mut W) -> Result<()> {
    master.write_all(b"n\n").await?;
    Ok(())
}

pub async fn inject_ctrl_c<W: AsyncWrite + Unpin>(master: &mut W) -> Result<()> {
    master.write_all(&[0x03]).await?;
    Ok(())
}
