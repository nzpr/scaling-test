#!/usr/bin/env python3
import argparse
import csv
import json

HEADER = ["runtime", "tasks", "run", "elapsed_ms", "checksum", "fib_n", "yield_every"]


def normalize_runtime(base_runtime: str, benchmark_name: str) -> str:
    if benchmark_name.endswith(".parallel"):
        return f"{base_runtime}-parallel"
    if benchmark_name.endswith(".concurrent"):
        return f"{base_runtime}-concurrent"
    suffix = benchmark_name.rsplit(".", 1)[-1]
    return f"{base_runtime}-{suffix}"


def emit_row(runtime: str, tasks: str, run_idx: int, elapsed_ms: float, fib_n: str, yield_every: str):
    return {
        "runtime": runtime,
        "tasks": tasks,
        "run": str(run_idx),
        "elapsed_ms": f"{elapsed_ms:.6f}",
        "checksum": "0",
        "fib_n": fib_n,
        "yield_every": yield_every,
    }


def load_json_rows(path: str):
    with open(path, encoding="utf-8") as f:
        payload = json.load(f)

    if isinstance(payload, list):
        return payload
    raise ValueError("expected JMH JSON payload to be a list")


def convert_rows(input_rows, base_runtime: str):
    out_rows = []

    for entry in input_rows:
        benchmark = entry.get("benchmark", "")
        params = entry.get("params", {})
        tasks = params.get("tasks")
        fib_n = params.get("fibN")
        yield_every = params.get("yieldEvery")

        if not benchmark or not tasks or not fib_n or not yield_every:
            continue

        runtime = normalize_runtime(base_runtime, benchmark)
        primary = entry.get("primaryMetric", {})
        raw_data = primary.get("rawData") or []
        run_idx = 1

        for fork_samples in raw_data:
            for sample in fork_samples:
                out_rows.append(
                    emit_row(
                        runtime=runtime,
                        tasks=tasks,
                        run_idx=run_idx,
                        elapsed_ms=float(sample),
                        fib_n=fib_n,
                        yield_every=yield_every,
                    )
                )
                run_idx += 1

        if run_idx == 1 and primary.get("score") is not None:
            out_rows.append(
                emit_row(
                    runtime=runtime,
                    tasks=tasks,
                    run_idx=run_idx,
                    elapsed_ms=float(primary["score"]),
                    fib_n=fib_n,
                    yield_every=yield_every,
                )
            )

    out_rows.sort(key=lambda row: (row["runtime"], int(row["tasks"]), int(row["run"])))
    return out_rows


def main() -> None:
    parser = argparse.ArgumentParser(description="Convert JMH JSON results to benchmark row format")
    parser.add_argument("--input", required=True, help="Input JMH JSON file")
    parser.add_argument("--output", required=True, help="Output normalized CSV")
    parser.add_argument("--runtime", required=True, help="Runtime base label (e.g. ce2.5.5-jvm)")
    args = parser.parse_args()

    out_rows = convert_rows(load_json_rows(args.input), args.runtime)

    with open(args.output, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=HEADER)
        writer.writeheader()
        writer.writerows(out_rows)


if __name__ == "__main__":
    main()
