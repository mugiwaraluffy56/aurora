use clap::Args;

use crate::config::load_config;
use crate::error::{Result, NodError};
use crate::pty::wrapper::PtyWrapper;

#[derive(Args, Debug)]
pub struct ExecArgs {
    #[arg(long, value_name = "AGENT")]
    pub agent: String,

    #[arg(long, default_value = "30000")]
    pub timeout: u64,

    #[arg(last = true, required = true)]
    pub command: Vec<String>,
}

pub async fn run(args: ExecArgs) -> Result<i32> {
    if args.command.is_empty() {
        return Err(NodError::Config("no command provided".to_string()));
    }

    let config = load_config()?;

    let patterns = match args.agent.to_lowercase().as_str() {
        "codex" => config.adapters.codex.prompt_patterns.clone(),
        "aider" => config.adapters.aider.prompt_patterns.clone(),
        "gemini" => config.adapters.gemini.prompt_patterns.clone(),
        other => {
            return Err(NodError::Config(format!(
                "unknown agent type: {other}. Valid: codex, aider, gemini"
            )));
        }
    };

    let cmd = args.command[0].clone();
    let cmd_args = args.command[1..].to_vec();

    let wrapper = PtyWrapper {
        command: cmd,
        args: cmd_args,
        patterns,
        timeout_ms: args.timeout,
    };

    wrapper.run().await
}
