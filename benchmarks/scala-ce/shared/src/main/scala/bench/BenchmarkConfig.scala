package bench

final case class BenchmarkConfig(
    runtimeLabel: String,
    tasks: Vector[Int],
    fibN: Int,
    yieldEvery: Int,
    repeats: Int,
    warmup: Boolean,
    output: String
)
