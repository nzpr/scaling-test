use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::Path;
use std::time::Instant;
use tokio::task::JoinSet;

const HEADER: &str = "runtime,tasks,run,elapsed_ms,checksum,fib_n,yield_every";

#[derive(Debug, Clone)]
struct Config {
    runtime_label: String,
    tasks: Vec<usize>,
    fib_n: u32,
    yield_every: u32,
    repeats: usize,
    warmup: bool,
    output: String,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            runtime_label: "tokio-rust".to_string(),
            tasks: vec![512, 1024, 2048, 4096, 8192, 16384],
            fib_n: 4096,
            yield_every: 64,
            repeats: 3,
            warmup: true,
            output: "../results/raw/tokio-rust.csv".to_string(),
        }
    }
}

async fn fib_stack_safe(n: u32, yield_every: u32) -> u64 {
    let mut a: u64 = 0;
    let mut b: u64 = 1;

    for i in 1..=n {
        if yield_every > 0 && i % yield_every == 0 {
            tokio::task::yield_now().await;
        }
        let c = a.wrapping_add(b);
        a = b;
        b = c;
    }

    a
}

async fn run_batch(tasks: usize, fib_n: u32, yield_every: u32) -> u64 {
    let mut set = JoinSet::new();

    for _ in 0..tasks {
        set.spawn(fib_stack_safe(fib_n, yield_every));
    }

    let mut checksum: u64 = 0;
    while let Some(result) = set.join_next().await {
        checksum ^= result.expect("task join failure");
    }

    checksum
}

fn parse_tasks(raw: &str) -> Result<Vec<usize>, String> {
    let values: Result<Vec<_>, _> = raw
        .split(',')
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(str::parse::<usize>)
        .collect();

    let values = values.map_err(|_| format!("invalid --tasks value: {raw}"))?;
    if values.is_empty() {
        return Err("--tasks must not be empty".to_string());
    }
    if values.iter().any(|v| *v == 0) {
        return Err("--tasks values must be > 0".to_string());
    }

    Ok(values)
}

fn parse_args() -> Result<Config, String> {
    let mut cfg = Config::default();
    let mut it = std::env::args().skip(1);

    while let Some(flag) = it.next() {
        let value = it
            .next()
            .ok_or_else(|| format!("missing value for {flag}"))?;

        match flag.as_str() {
            "--runtime" => cfg.runtime_label = value,
            "--tasks" => cfg.tasks = parse_tasks(&value)?,
            "--fib" => cfg.fib_n = value.parse().map_err(|_| format!("invalid --fib value: {value}"))?,
            "--yield-every" => {
                cfg.yield_every = value
                    .parse()
                    .map_err(|_| format!("invalid --yield-every value: {value}"))?
            }
            "--repeats" => {
                cfg.repeats = value
                    .parse()
                    .map_err(|_| format!("invalid --repeats value: {value}"))?
            }
            "--warmup" => {
                cfg.warmup = match value.to_ascii_lowercase().as_str() {
                    "true" => true,
                    "false" => false,
                    _ => return Err(format!("invalid --warmup value: {value}")),
                }
            }
            "--output" => cfg.output = value,
            _ => return Err(format!("unknown flag: {flag}")),
        }
    }

    Ok(cfg)
}

fn write_csv(path: &str, rows: &[String]) -> Result<(), String> {
    let output_path = Path::new(path);
    if let Some(parent) = output_path.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("failed to create output directory: {e}"))?;
    }

    let needs_header = !output_path.exists() || fs::metadata(output_path).map_err(|e| e.to_string())?.len() == 0;
    let mut file = OpenOptions::new()
        .create(true)
        .append(true)
        .open(output_path)
        .map_err(|e| format!("failed to open output file: {e}"))?;

    if needs_header {
        writeln!(file, "{HEADER}").map_err(|e| e.to_string())?;
    }

    for row in rows {
        writeln!(file, "{row}").map_err(|e| e.to_string())?;
    }

    Ok(())
}

#[tokio::main(flavor = "multi_thread")]
async fn main() {
    let cfg = match parse_args() {
        Ok(c) => c,
        Err(e) => {
            eprintln!("error: {e}");
            std::process::exit(1);
        }
    };

    if cfg.warmup {
        let warmup_tasks = cfg.tasks[0];
        println!(
            "warmup runtime={} tasks={} fib={} yieldEvery={}",
            cfg.runtime_label, warmup_tasks, cfg.fib_n, cfg.yield_every
        );
        let _ = run_batch(warmup_tasks, cfg.fib_n, cfg.yield_every).await;
    }

    let mut rows = Vec::new();
    for tasks in &cfg.tasks {
        for run_idx in 1..=cfg.repeats {
            let started = Instant::now();
            let checksum = run_batch(*tasks, cfg.fib_n, cfg.yield_every).await;
            let elapsed_ms = started.elapsed().as_secs_f64() * 1000.0;

            let row = format!(
                "{},{},{},{:.3},{},{},{}",
                cfg.runtime_label, tasks, run_idx, elapsed_ms, checksum, cfg.fib_n, cfg.yield_every
            );
            println!("{row}");
            rows.push(row);
        }
    }

    if let Err(e) = write_csv(&cfg.output, &rows) {
        eprintln!("error: {e}");
        std::process::exit(1);
    }
}
