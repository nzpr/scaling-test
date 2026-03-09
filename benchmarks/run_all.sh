#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCALA_DIR="$ROOT/scala-ce"
RUST_DIR="$ROOT/tokio-rust"
OUT_DIR="${1:-$ROOT/results/raw}"

TASKS="${TASKS:-512,1024,2048,4096,8192,16384}"
FIB_N="${FIB_N:-4096}"
YIELD_EVERY="${YIELD_EVERY:-64}"
REPEATS="${REPEATS:-3}"
WARMUP="${WARMUP:-true}"

mkdir -p "$OUT_DIR"

run_ce_project() {
  local project="$1"
  local runtime_label="$2"
  local out_file="$OUT_DIR/${runtime_label}.csv"

  rm -f "$out_file"
  (
    cd "$SCALA_DIR"
    sbt --batch \
      "$project/runMain bench.BenchmarkMain --runtime $runtime_label --tasks $TASKS --fib $FIB_N --yield-every $YIELD_EVERY --repeats $REPEATS --warmup $WARMUP --output $out_file"
  )
}

run_ce_native() {
  local project="ce370native"
  local runtime_label="ce3.7.0-native"
  local out_file="$OUT_DIR/${runtime_label}.csv"
  local native_bin="$SCALA_DIR/ce370native/target/scala-2.13/native/bench.BenchmarkMain"

  rm -f "$out_file"
  (
    cd "$SCALA_DIR"
    sbt --batch "$project/nativeLink"
  )

  "$native_bin" \
    --runtime "$runtime_label" \
    --tasks "$TASKS" \
    --fib "$FIB_N" \
    --yield-every "$YIELD_EVERY" \
    --repeats "$REPEATS" \
    --warmup "$WARMUP" \
    --output "$out_file"
}

run_rust() {
  local runtime_label="tokio-rust"
  local out_file="$OUT_DIR/${runtime_label}.csv"

  rm -f "$out_file"
  (
    cd "$RUST_DIR"
    cargo run --release -- \
      --runtime "$runtime_label" \
      --tasks "$TASKS" \
      --fib "$FIB_N" \
      --yield-every "$YIELD_EVERY" \
      --repeats "$REPEATS" \
      --warmup "$WARMUP" \
      --output "$out_file"
  )
}

run_ce_project ce255jvm ce2.5.5-jvm
run_ce_project ce370jvm ce3.7.0-jvm
run_ce_native
run_rust

ALL_CSV="$OUT_DIR/all.csv"
echo "runtime,tasks,run,elapsed_ms,checksum,fib_n,yield_every" > "$ALL_CSV"
for runtime in ce2.5.5-jvm ce3.7.0-jvm ce3.7.0-native tokio-rust; do
  tail -n +2 "$OUT_DIR/${runtime}.csv" >> "$ALL_CSV"
done

python3 "$ROOT/plot.py" --input "$ALL_CSV" --output "$ROOT/results/time-vs-tasks.png"

echo "raw_csv=$ALL_CSV"
echo "chart=$ROOT/results/time-vs-tasks.png"
