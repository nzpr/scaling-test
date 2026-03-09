# Scheduling Scalability Benchmark Results (Presentation)

## What Was Measured
This suite measures **time spent vs number of tasks** for a stack-safe Fibonacci workload that yields periodically to stress the scheduler.

Compared runtimes and strategies:
- `ce2.5.5-jvm-parallel`
- `ce2.5.5-jvm-concurrent`
- `ce3.7.0-jvm-parallel`
- `ce3.7.0-jvm-concurrent`
- `ce3.7.0-native-parallel`
- `ce3.7.0-native-concurrent`
- `tokio-rust`

Requested versions were CE `2.6.0` and `2.7.0`; those artifacts are not published on Maven Central. The executable CE2 baseline in this repo is `2.5.5`.

## Workload + Parameters
- Fibonacci size: `fibN=2048`
- Yield cadence: `yieldEvery=64`
- Task sweep: `128,256,512,1024,2048`
- Maximum concurrency pattern: spawn all tasks up-front, then join all

Latest suite command used:

```bash
cd /workspace/benchmarks
TASKS='128,256,512,1024,2048' FIB_N=2048 REPEATS=2 YIELD_EVERY=64 PYTHON_BIN="$PWD/.venv/bin/python" PATH="$PWD/.venv/bin:$PATH" ./run_all.sh
```

Rust note: crates.io access intermittently returned `403` in this environment; Tokio rows were produced via the already-built binary:

```bash
./tokio-rust/target/release/tokio-rust-bench --runtime tokio-rust --tasks 128,256,512,1024,2048 --fib 2048 --yield-every 64 --repeats 2 --warmup true --output /workspace/benchmarks/results/raw/tokio-rust.csv
```

## Where Parallel/Concurrent Actually Happens
These are the exact call sites that define the strategy under test.

- CE2 JVM `Parallel`: [scala-ce/ce255jmh/src/main/scala/bench/jmh/CE25SchedulerBenchmark.scala#L43](scala-ce/ce255jmh/src/main/scala/bench/jmh/CE25SchedulerBenchmark.scala#L43)
- CE2 JVM `Concurrent`: [scala-ce/ce255jmh/src/main/scala/bench/jmh/CE25SchedulerBenchmark.scala#L52](scala-ce/ce255jmh/src/main/scala/bench/jmh/CE25SchedulerBenchmark.scala#L52)
- CE3 JVM `Parallel`: [scala-ce/ce370jmh/src/main/scala/bench/jmh/CE37SchedulerBenchmark.scala#L40](scala-ce/ce370jmh/src/main/scala/bench/jmh/CE37SchedulerBenchmark.scala#L40)
- CE3 JVM `Concurrent`: [scala-ce/ce370jmh/src/main/scala/bench/jmh/CE37SchedulerBenchmark.scala#L49](scala-ce/ce370jmh/src/main/scala/bench/jmh/CE37SchedulerBenchmark.scala#L49)
- CE3 Native `Parallel` (shared CE3 workload): [scala-ce/ce370shared/src/main/scala/bench/Workload.scala#L23](scala-ce/ce370shared/src/main/scala/bench/Workload.scala#L23)
- CE3 Native `Concurrent` (shared CE3 workload): [scala-ce/ce370shared/src/main/scala/bench/Workload.scala#L31](scala-ce/ce370shared/src/main/scala/bench/Workload.scala#L31)
- Tokio spawn/join: [tokio-rust/src/main.rs#L54](tokio-rust/src/main.rs#L54)

Yield call sites:
- CE2 `IO.shift`: [scala-ce/ce255jmh/src/main/scala/bench/jmh/CE25SchedulerBenchmark.scala#L32](scala-ce/ce255jmh/src/main/scala/bench/jmh/CE25SchedulerBenchmark.scala#L32)
- CE3 `IO.cede`: [scala-ce/ce370jmh/src/main/scala/bench/jmh/CE37SchedulerBenchmark.scala#L29](scala-ce/ce370jmh/src/main/scala/bench/jmh/CE37SchedulerBenchmark.scala#L29)
- Tokio `yield_now`: [tokio-rust/src/main.rs#L40](tokio-rust/src/main.rs#L40)

## Shared Scala Code (No Repetition)
- Shared CLI/config/CSV runner: [scala-ce/shared/src/main/scala/bench/BenchmarkRunner.scala](scala-ce/shared/src/main/scala/bench/BenchmarkRunner.scala)
- Shared CE3 workload used by JVM/Native app paths: [scala-ce/ce370shared/src/main/scala/bench/Workload.scala](scala-ce/ce370shared/src/main/scala/bench/Workload.scala)

## Measurement Method
- CE JVM uses JMH with stronger stability defaults:
  - `WARMUP_ITERS=8`, `WARMUP_TIME=2s`
  - `MEASURE_ITERS=8`, `MEASURE_TIME=2s`
  - `FORKS=3`
  - `JMH_JVM_ARGS="-Xms4g -Xmx4g -XX:+UseG1GC"`
- CE JVM CSVs are normalized from JMH JSON raw iteration samples (`primaryMetric.rawData`), so each CE JVM point contains `24` run samples (`3 forks x 8 measurements`).
- CE native and Tokio rows are from benchmark repeats (`2` per task in this run).
- Charts in `results/` are currently **median-only**.

## Why This Is Representative (Semantic Parity)
The benchmark was designed so each runtime executes the same logical workload and scheduling shape:

- Same algorithm: iterative, stack-safe Fibonacci loop in every implementation.
  - CE2 loop: [scala-ce/ce255jvm/src/main/scala/bench/Workload.scala#L11](scala-ce/ce255jvm/src/main/scala/bench/Workload.scala#L11)
  - CE3 loop (shared for CE3 app/native): [scala-ce/ce370shared/src/main/scala/bench/Workload.scala#L8](scala-ce/ce370shared/src/main/scala/bench/Workload.scala#L8)
  - Tokio loop: [tokio-rust/src/main.rs#L34](tokio-rust/src/main.rs#L34)

- Same cooperative-yield policy: yield every `yieldEvery` iterations.
  - CE2: `IO.shift` [scala-ce/ce255jvm/src/main/scala/bench/Workload.scala#L16](scala-ce/ce255jvm/src/main/scala/bench/Workload.scala#L16)
  - CE3: `IO.cede` [scala-ce/ce370shared/src/main/scala/bench/Workload.scala#L13](scala-ce/ce370shared/src/main/scala/bench/Workload.scala#L13)
  - Tokio: `yield_now` [tokio-rust/src/main.rs#L40](tokio-rust/src/main.rs#L40)

- Same concurrency shape: create all tasks first, then await all.
  - CE `Parallel`: `parTraverse` call sites (listed above).
  - CE `Concurrent`: `start` + `join`/`joinWithNever` call sites (listed above).
  - Tokio: `JoinSet::spawn` then `join_next` [tokio-rust/src/main.rs#L54](tokio-rust/src/main.rs#L54), [tokio-rust/src/main.rs#L58](tokio-rust/src/main.rs#L58)

- Same input parameterization and CSV schema across runtimes:
  - `tasks`, `fib_n`, `yield_every`, `runtime`, `elapsed_ms` columns
  - Shared Scala runner for app/native: [scala-ce/shared/src/main/scala/bench/BenchmarkRunner.scala](scala-ce/shared/src/main/scala/bench/BenchmarkRunner.scala)

- Same result-consumption intent (avoid eliding work):
  - CE JMH benchmark methods return `Long` to JMH (captured as benchmark output).
  - Native/Tokio explicitly compute and emit XOR checksum across task results.

What is intentionally different:
- CE JVM is measured with JMH (fork/warmup/measurement iterations), while native/Tokio use repeated wall-clock batch timing.
- This makes **within-engine comparisons** very robust (CE2 vs CE3 JVM, parallel vs concurrent), while cross-engine comparisons should still be interpreted with normal harness caveats.

## Results (Median ms/op)
Source: [results/raw/all.csv](results/raw/all.csv)

| Runtime | 128 | 256 | 512 | 1024 | 2048 |
|---|---:|---:|---:|---:|---:|
| ce2.5.5-jvm-concurrent | 175.433 | 99.931 | 195.073 | 288.316 | 437.969 |
| ce2.5.5-jvm-parallel | 27.820 | 51.430 | 130.623 | 190.469 | 367.496 |
| ce3.7.0-jvm-concurrent | 32.491 | 54.468 | 111.138 | 227.216 | 477.308 |
| ce3.7.0-jvm-parallel | 32.878 | 68.020 | 128.026 | 288.178 | 527.802 |
| ce3.7.0-native-concurrent | 2.849 | 4.594 | 7.628 | 19.772 | 36.954 |
| ce3.7.0-native-parallel | 27.562 | 6.314 | 18.272 | 43.572 | 52.888 |
| tokio-rust | 0.633 | 1.038 | 1.610 | 2.091 | 6.208 |

Ranking at `tasks=2048` (lower is better, median):
1. `tokio-rust`: `6.208 ms`
2. `ce3.7.0-native-concurrent`: `36.954 ms`
3. `ce3.7.0-native-parallel`: `52.888 ms`
4. `ce2.5.5-jvm-parallel`: `367.496 ms`
5. `ce2.5.5-jvm-concurrent`: `437.969 ms`
6. `ce3.7.0-jvm-concurrent`: `477.308 ms`
7. `ce3.7.0-jvm-parallel`: `527.802 ms`

## Charts To Present
Combined:
- Linear: [results/time-vs-tasks.png](results/time-vs-tasks.png)
- Log: [results/time-vs-tasks-log.png](results/time-vs-tasks-log.png)

Per-series (linear):
- [results/time-vs-tasks-tokio-rust.png](results/time-vs-tasks-tokio-rust.png)
- [results/time-vs-tasks-ce2.5.5-jvm-parallel.png](results/time-vs-tasks-ce2.5.5-jvm-parallel.png)
- [results/time-vs-tasks-ce2.5.5-jvm-concurrent.png](results/time-vs-tasks-ce2.5.5-jvm-concurrent.png)
- [results/time-vs-tasks-ce3.7.0-jvm-parallel.png](results/time-vs-tasks-ce3.7.0-jvm-parallel.png)
- [results/time-vs-tasks-ce3.7.0-jvm-concurrent.png](results/time-vs-tasks-ce3.7.0-jvm-concurrent.png)
- [results/time-vs-tasks-ce3.7.0-native-parallel.png](results/time-vs-tasks-ce3.7.0-native-parallel.png)
- [results/time-vs-tasks-ce3.7.0-native-concurrent.png](results/time-vs-tasks-ce3.7.0-native-concurrent.png)

## Environment (Latest Run)
- Host: `Linux 41aaa1a2113a 6.10.14-linuxkit #1 SMP Fri Nov 29 17:22:03 UTC 2024 aarch64 aarch64 aarch64 GNU/Linux`
- Java: `OpenJDK 21.0.10`
- SBT: `1.12.5`
- Rust: `rustc 1.94.0`, `cargo 1.94.0`
