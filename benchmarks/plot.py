#!/usr/bin/env python3
import argparse
import csv
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


def main():
    parser = argparse.ArgumentParser(description="Plot time-vs-tasks benchmark results")
    parser.add_argument("--input", required=True, help="Input CSV with benchmark rows")
    parser.add_argument("--output", required=True, help="Output PNG file path")
    args = parser.parse_args()

    rows = load_rows(args.input)
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
        plt.plot(tasks, medians, marker="o", linewidth=2, label=runtime)

    plt.title("Stack-Safe Fibonacci Scheduling Scalability")
    plt.xlabel("Number of Tasks")
    plt.ylabel("Time Spent (ms, median of repeats)")
    plt.grid(True, alpha=0.3)
    plt.legend()
    plt.tight_layout()
    plt.savefig(args.output, dpi=160)


if __name__ == "__main__":
    main()
