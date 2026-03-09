#!/usr/bin/env python3
import argparse
import csv
import math
import statistics
from collections import defaultdict

import matplotlib.pyplot as plt


def load_rows(path):
    rows = []
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append(
                {
                    "runtime": row["runtime"],
                    "tasks": int(row["tasks"]),
                    "elapsed_ms": float(row["elapsed_ms"]),
                }
            )
    return rows


def percentile(values, p):
    ordered = sorted(values)
    if not ordered:
        raise ValueError("cannot compute percentile of empty list")
    if len(ordered) == 1:
        return ordered[0]

    rank = (p / 100.0) * (len(ordered) - 1)
    lower = math.floor(rank)
    upper = math.ceil(rank)
    if lower == upper:
        return ordered[lower]

    weight = rank - lower
    return ordered[lower] * (1.0 - weight) + ordered[upper] * weight


def main():
    parser = argparse.ArgumentParser(description="Plot time-vs-tasks benchmark results")
    parser.add_argument("--input", required=True, help="Input CSV with benchmark rows")
    parser.add_argument("--output", required=True, help="Output PNG file path")
    parser.add_argument("--scale", choices=["linear", "log"], default="linear", help="Axis scale")
    parser.add_argument("--runtime", default=None, help="Filter to a single runtime label")
    parser.add_argument("--show-p95", action="store_true", help="Overlay p95 in addition to median")
    args = parser.parse_args()

    rows = load_rows(args.input)
    if args.runtime:
        rows = [row for row in rows if row["runtime"] == args.runtime]
        if not rows:
            raise SystemExit(f"no rows found for runtime={args.runtime}")

    grouped = defaultdict(list)
    runtimes = []

    for row in rows:
        key = (row["runtime"], row["tasks"])
        grouped[key].append(row["elapsed_ms"])
        if row["runtime"] not in runtimes:
            runtimes.append(row["runtime"])

    plt.figure(figsize=(12, 7))

    for runtime in runtimes:
        tasks = sorted({task for r, task in grouped.keys() if r == runtime})
        medians = [statistics.median(grouped[(runtime, task)]) for task in tasks]
        (median_line,) = plt.plot(tasks, medians, marker="o", linewidth=2, label=f"{runtime} median")

        if args.show_p95:
            p95 = [percentile(grouped[(runtime, task)], 95) for task in tasks]
            plt.plot(
                tasks,
                p95,
                marker="x",
                linewidth=1.6,
                linestyle="--",
                color=median_line.get_color(),
                alpha=0.8,
                label=f"{runtime} p95",
            )

    title_runtime = args.runtime if args.runtime else "All Runtimes"
    title_scale = "Log Scale" if args.scale == "log" else "Linear Scale"
    plt.title(f"Stack-Safe Fibonacci Scheduling Scalability ({title_runtime}, {title_scale})")
    plt.xlabel("Number of Tasks")
    if args.show_p95:
        plt.ylabel("Time Spent (ms/op, median and p95 of runs)")
    else:
        plt.ylabel("Time Spent (ms/op, median of runs)")
    if args.scale == "log":
        plt.xscale("log", base=2)
        plt.yscale("log", base=10)
    plt.grid(True, alpha=0.3)
    plt.legend(ncol=2 if args.show_p95 else 1, fontsize=9)
    plt.tight_layout()
    plt.savefig(args.output, dpi=160)


if __name__ == "__main__":
    main()
