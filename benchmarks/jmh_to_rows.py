#!/usr/bin/env python3
import argparse
import csv

HEADER = ["runtime", "tasks", "run", "elapsed_ms", "checksum", "fib_n", "yield_every"]


def normalize_runtime(base_runtime: str, benchmark_name: str) -> str:
    if benchmark_name.endswith(".parallel"):
        return f"{base_runtime}-parallel"
    if benchmark_name.endswith(".concurrent"):
        return f"{base_runtime}-concurrent"
    suffix = benchmark_name.rsplit(".", 1)[-1]
    return f"{base_runtime}-{suffix}"


def main() -> None:
    parser = argparse.ArgumentParser(description="Convert JMH CSV results to benchmark row format")
    parser.add_argument("--input", required=True, help="Input JMH CSV file")
    parser.add_argument("--output", required=True, help="Output normalized CSV")
    parser.add_argument("--runtime", required=True, help="Runtime base label (e.g. ce2.5.5-jvm)")
    args = parser.parse_args()

    with open(args.input, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    out_rows = []
    for row in rows:
        benchmark = row.get("Benchmark", "")
        score = row.get("Score")
        tasks = row.get("Param: tasks")
        fib_n = row.get("Param: fibN")
        yield_every = row.get("Param: yieldEvery")

        if not benchmark or not score or not tasks or not fib_n or not yield_every:
            continue

        runtime = normalize_runtime(args.runtime, benchmark)
        out_rows.append(
            {
                "runtime": runtime,
                "tasks": tasks,
                "run": "1",
                "elapsed_ms": score,
                "checksum": "0",
                "fib_n": fib_n,
                "yield_every": yield_every,
            }
        )

    with open(args.output, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=HEADER)
        writer.writeheader()
        writer.writerows(out_rows)


if __name__ == "__main__":
    main()
