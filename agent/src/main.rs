use clap::{Parser, Subcommand};
use tracing_subscriber::EnvFilter;

mod adapters;
mod cli;
mod config;
mod crypto;
mod daemon;
mod error;
mod http;
mod pty;
mod session;
mod ws;

use cli::{
    ask::AskArgs,
    daemon::DaemonArgs,
    exec::ExecArgs,
    hook::HookArgs,
    init::InitArgs,
    rotate::RotateArgs,
    status::StatusArgs,
};

#[derive(Parser, Debug)]
#[command(name = "tap-guard")]
#[command(version = env!("CARGO_PKG_VERSION"))]
#[command(about = "Nod — mobile approval for Claude Code tool calls")]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand, Debug)]
enum Commands {
    /// Initialize Nod on this machine (register with server)
    Init(InitArgs),

    /// Claude Code PreToolUse hook — reads stdin, sends to daemon, exits 0 or 2
    Hook(HookArgs),

    /// PTY wrapper for Codex / Aider / Gemini
    Exec(ExecArgs),

    /// One-shot approval request
    Ask(AskArgs),

    /// Start the background daemon
    Daemon(DaemonArgs),

    /// Show machine / session / connection status
    Status(StatusArgs),

    /// Rotate machine token
    Rotate(RotateArgs),
}

#[tokio::main]
async fn main() {
    // Initialize tracing; default to WARN to keep hook fast
    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("warn")),
        )
        .with_writer(std::io::stderr)
        .init();

    let cli = Cli::parse();

    let result = match cli.command {
        Commands::Init(args) => cli::init::run(args).await.map(|_| 0),
        Commands::Hook(args) => cli::hook::run(args).await,
        Commands::Exec(args) => cli::exec::run(args).await,
        Commands::Ask(args) => cli::ask::run(args).await,
        Commands::Daemon(args) => cli::daemon::run(args).await.map(|_| 0),
        Commands::Status(args) => cli::status::run(args).await.map(|_| 0),
        Commands::Rotate(args) => cli::rotate::run(args).await.map(|_| 0),
    };

    match result {
        Ok(code) => std::process::exit(code),
        Err(e) => {
            eprintln!("error: {e}");
            std::process::exit(1);
        }
    }
}
