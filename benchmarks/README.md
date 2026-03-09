# Scheduling Scalability Benchmarks

This benchmark suite compares time spent vs number of tasks for stack-safe Fibonacci workloads across:
- Cats Effect 2.5.5 on JVM
- Cats Effect 3.7.0 on JVM
- Cats Effect 3.7.0 on Scala Native 0.5
- Tokio on Rust

Note: Cats Effect `2.6.0` and `2.7.0` are not published artifacts on Maven Central, and Cats Effect 2.x is not available for Scala Native 0.5.

## Workload model
- One benchmark task computes Fibonacci in a stack-safe loop.
- Every `yieldEvery` iterations it yields (`IO.shift` on CE2, `IO.cede` on CE3, `tokio::task::yield_now` on Tokio) to stress scheduling.
- A benchmark batch spawns all tasks immediately to maximize runtime concurrency.
- Each `(runtime, tasks)` point runs multiple repeats and writes raw timings to CSV.

## Run

```bash
cd /workspace/benchmarks
python3 -m venv .venv
.venv/bin/pip install matplotlib
PATH="$PWD/.venv/bin:$PATH" ./run_all.sh
```

Tune sweep parameters via environment variables:

```bash
TASKS=1024,2048,4096,8192 FIB_N=4096 YIELD_EVERY=64 REPEATS=3 PATH="$PWD/.venv/bin:$PATH" ./run_all.sh
```

Outputs:
- Raw per-runtime CSVs in `benchmarks/results/raw/`
- Combined CSV: `benchmarks/results/raw/all.csv`
- Combined charts:
  - `benchmarks/results/time-vs-tasks.png` (linear)
  - `benchmarks/results/time-vs-tasks-log.png` (log)
- Per-runtime charts (each in linear + log):
  - `benchmarks/results/time-vs-tasks-ce2.5.5-jvm*.png`
  - `benchmarks/results/time-vs-tasks-ce3.7.0-jvm*.png`
  - `benchmarks/results/time-vs-tasks-ce3.7.0-native*.png`
  - `benchmarks/results/time-vs-tasks-tokio-rust*.png`
