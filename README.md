# Scheduling Scalability: Cats Effect vs Tokio

Started here https://x.com/JustDeezGuy/status/2030798686955925856

This repository benchmarks scheduler scalability using the same stack-safe Fibonacci workload across:
- Cats Effect 2.x on JVM (`ce2.5.5-jvm`)
- Cats Effect 3.7.0 on JVM (`ce3.7.0-jvm`)
- Cats Effect 3.7.0 on Scala Native 0.5 (`ce3.7.0-native`)
- Tokio on Rust (`tokio-rust`)

It compares two CE execution styles where applicable:
- `Parallel` (`parTraverse`)
- `Concurrent` (`start` + `join`/`joinWithNever`)

## Version Note
The original ask referenced CE `2.6.0` and `2.7.0`; those artifacts are not published on Maven Central. The executable CE2 baseline used here is `2.5.5`.

## Benchmark Design
Workload per task:
- Compute stack-safe Fibonacci with `fibN=2048`
- Yield every `yieldEvery=64` iterations

Sweep:
- `tasks = 128,256,512,1024,2048`

Concurrency pattern:
- Spawn all tasks eagerly
- Join all tasks

Latest full-suite command:

```bash
cd benchmarks
TASKS='128,256,512,1024,2048' FIB_N=2048 REPEATS=2 YIELD_EVERY=64 PYTHON_BIN="$PWD/.venv/bin/python" PATH="$PWD/.venv/bin:$PATH" ./run_all.sh
```

Rust fallback command used when crates.io was unavailable in this environment:

```bash
./tokio-rust/target/release/tokio-rust-bench --runtime tokio-rust --tasks 128,256,512,1024,2048 --fib 2048 --yield-every 64 --repeats 2 --warmup true --output /workspace/benchmarks/results/raw/tokio-rust.csv
```

## Why Results Are Representative (Semantic Parity)
The benchmark keeps semantics aligned across runtimes:

1. Same algorithm everywhere (iterative stack-safe Fibonacci loop).
2. Same cooperative-yield cadence (`yieldEvery=64`).
3. Same scheduling shape (spawn-all then join-all).
4. Same benchmark inputs and output schema (`runtime,tasks,run,elapsed_ms,checksum,fib_n,yield_every`).

Exact strategy call sites:
- CE2 JVM `Parallel`: [benchmarks/scala-ce/ce255jmh/src/main/scala/bench/jmh/CE25SchedulerBenchmark.scala#L43](benchmarks/scala-ce/ce255jmh/src/main/scala/bench/jmh/CE25SchedulerBenchmark.scala#L43)
- CE2 JVM `Concurrent`: [benchmarks/scala-ce/ce255jmh/src/main/scala/bench/jmh/CE25SchedulerBenchmark.scala#L52](benchmarks/scala-ce/ce255jmh/src/main/scala/bench/jmh/CE25SchedulerBenchmark.scala#L52)
- CE3 JVM `Parallel`: [benchmarks/scala-ce/ce370jmh/src/main/scala/bench/jmh/CE37SchedulerBenchmark.scala#L40](benchmarks/scala-ce/ce370jmh/src/main/scala/bench/jmh/CE37SchedulerBenchmark.scala#L40)
- CE3 JVM `Concurrent`: [benchmarks/scala-ce/ce370jmh/src/main/scala/bench/jmh/CE37SchedulerBenchmark.scala#L49](benchmarks/scala-ce/ce370jmh/src/main/scala/bench/jmh/CE37SchedulerBenchmark.scala#L49)
- CE3 Native `Parallel` (shared CE3 workload): [benchmarks/scala-ce/ce370shared/src/main/scala/bench/Workload.scala#L23](benchmarks/scala-ce/ce370shared/src/main/scala/bench/Workload.scala#L23)
- CE3 Native `Concurrent` (shared CE3 workload): [benchmarks/scala-ce/ce370shared/src/main/scala/bench/Workload.scala#L31](benchmarks/scala-ce/ce370shared/src/main/scala/bench/Workload.scala#L31)
- Tokio spawn/join: [benchmarks/tokio-rust/src/main.rs#L54](benchmarks/tokio-rust/src/main.rs#L54), [benchmarks/tokio-rust/src/main.rs#L58](benchmarks/tokio-rust/src/main.rs#L58)

Yield call sites:
- CE2 `IO.shift`: [benchmarks/scala-ce/ce255jmh/src/main/scala/bench/jmh/CE25SchedulerBenchmark.scala#L32](benchmarks/scala-ce/ce255jmh/src/main/scala/bench/jmh/CE25SchedulerBenchmark.scala#L32)
- CE3 `IO.cede`: [benchmarks/scala-ce/ce370jmh/src/main/scala/bench/jmh/CE37SchedulerBenchmark.scala#L29](benchmarks/scala-ce/ce370jmh/src/main/scala/bench/jmh/CE37SchedulerBenchmark.scala#L29)
- Tokio `yield_now`: [benchmarks/tokio-rust/src/main.rs#L40](benchmarks/tokio-rust/src/main.rs#L40)

Shared Scala code (avoids duplication):
- Shared runner: [benchmarks/scala-ce/shared/src/main/scala/bench/BenchmarkRunner.scala](benchmarks/scala-ce/shared/src/main/scala/bench/BenchmarkRunner.scala)
- Shared CE3 workload: [benchmarks/scala-ce/ce370shared/src/main/scala/bench/Workload.scala](benchmarks/scala-ce/ce370shared/src/main/scala/bench/Workload.scala)

## Measurement Method
- CE JVM uses JMH with stronger stability defaults:
  - `WARMUP_ITERS=8`, `WARMUP_TIME=2s`
  - `MEASURE_ITERS=8`, `MEASURE_TIME=2s`
  - `FORKS=3`
  - `JMH_JVM_ARGS="-Xms4g -Xmx4g -XX:+UseG1GC"`
- CE JVM rows are normalized from JMH JSON raw iteration samples (`3 forks x 8 iters = 24 samples per point`).
- CE native and Tokio rows use benchmark repeats (`2` per point in the latest run).
- Charts are currently median-only.

## Latest Results (Median ms/op)
Source: [benchmarks/results/raw/all.csv](benchmarks/results/raw/all.csv)

| Runtime | 128 | 256 | 512 | 1024 | 2048 |
|---|---:|---:|---:|---:|---:|
| ce2.5.5-jvm-concurrent | 175.433 | 99.931 | 195.073 | 288.316 | 437.969 |
| ce2.5.5-jvm-parallel | 27.820 | 51.430 | 130.623 | 190.469 | 367.496 |
| ce3.7.0-jvm-concurrent | 32.491 | 54.468 | 111.138 | 227.216 | 477.308 |
| ce3.7.0-jvm-parallel | 32.878 | 68.020 | 128.026 | 288.178 | 527.802 |
| ce3.7.0-native-concurrent | 2.849 | 4.594 | 7.628 | 19.772 | 36.954 |
| ce3.7.0-native-parallel | 27.562 | 6.314 | 18.272 | 43.572 | 52.888 |
| tokio-rust | 0.633 | 1.038 | 1.610 | 2.091 | 6.208 |

Ranking at `tasks=2048` (lower is better):
1. `tokio-rust` (`6.208 ms`)
2. `ce3.7.0-native-concurrent` (`36.954 ms`)
3. `ce3.7.0-native-parallel` (`52.888 ms`)
4. `ce2.5.5-jvm-parallel` (`367.496 ms`)
5. `ce2.5.5-jvm-concurrent` (`437.969 ms`)
6. `ce3.7.0-jvm-concurrent` (`477.308 ms`)
7. `ce3.7.0-jvm-parallel` (`527.802 ms`)

## Charts
Combined:
- [Linear](benchmarks/results/time-vs-tasks.png)
- [Log](benchmarks/results/time-vs-tasks-log.png)

Per series (linear):
- [tokio-rust](benchmarks/results/time-vs-tasks-tokio-rust.png)
- [ce2.5.5-jvm-parallel](benchmarks/results/time-vs-tasks-ce2.5.5-jvm-parallel.png)
- [ce2.5.5-jvm-concurrent](benchmarks/results/time-vs-tasks-ce2.5.5-jvm-concurrent.png)
- [ce3.7.0-jvm-parallel](benchmarks/results/time-vs-tasks-ce3.7.0-jvm-parallel.png)
- [ce3.7.0-jvm-concurrent](benchmarks/results/time-vs-tasks-ce3.7.0-jvm-concurrent.png)
- [ce3.7.0-native-parallel](benchmarks/results/time-vs-tasks-ce3.7.0-native-parallel.png)
- [ce3.7.0-native-concurrent](benchmarks/results/time-vs-tasks-ce3.7.0-native-concurrent.png)

## Artifacts
- Combined CSV: [benchmarks/results/raw/all.csv](benchmarks/results/raw/all.csv)
- CE2 JMH raw JSON: [benchmarks/results/raw/ce2.5.5-jvm-jmh-raw.json](benchmarks/results/raw/ce2.5.5-jvm-jmh-raw.json)
- CE3 JMH raw JSON: [benchmarks/results/raw/ce3.7.0-jvm-jmh-raw.json](benchmarks/results/raw/ce3.7.0-jvm-jmh-raw.json)

## Environment (Latest Run)
- Host: `Linux 41aaa1a2113a 6.10.14-linuxkit #1 SMP Fri Nov 29 17:22:03 UTC 2024 aarch64 aarch64 aarch64 GNU/Linux`
- Java: `OpenJDK 21.0.10`
- SBT: `1.12.5`
- Rust: `rustc 1.94.0`, `cargo 1.94.0`

## Reproduce
```bash
cd benchmarks
python3 -m venv .venv
.venv/bin/pip install matplotlib
PATH="$PWD/.venv/bin:$PATH" ./run_all.sh
```
