package bench

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._

object BenchmarkMain extends IOApp {

  private val DefaultConfig = BenchmarkConfig(
    runtimeLabel = "ce3.7.0-native",
    tasks = Vector(512, 1024, 2048, 4096, 8192, 16384),
    fibN = 4096,
    yieldEvery = 64,
    repeats = 3,
    warmup = true,
    output = "../results/raw/ce3.7.0-native.csv"
  )

  override def run(args: List[String]): IO[ExitCode] =
    BenchmarkCli.parseArgs(args, DefaultConfig) match {
      case Left(err)  => IO(println(s"error: $err")).as(ExitCode.Error)
      case Right(cfg) => BenchmarkRunner.run(cfg, Workload.runBatchParallel)
    }
}
