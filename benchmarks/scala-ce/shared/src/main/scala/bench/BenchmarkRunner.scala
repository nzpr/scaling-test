package bench

import cats.effect.{ExitCode, IO}
import cats.implicits._

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.util.Locale

object BenchmarkRunner {

  private final val Header = "runtime,tasks,run,elapsed_ms,checksum,fib_n,yield_every"

  private def timeOne(
      tasks: Int,
      runIdx: Int,
      cfg: BenchmarkConfig,
      runBatch: (Int, Int, Int) => IO[Long]
  ): IO[String] =
    for {
      start <- IO(System.nanoTime())
      checksum <- runBatch(tasks, cfg.fibN, cfg.yieldEvery)
      end <- IO(System.nanoTime())
      elapsedMs = (end - start) / 1000000.0
      elapsedText = java.lang.String.format(Locale.US, "%.3f", Double.box(elapsedMs))
      row = s"${cfg.runtimeLabel},$tasks,$runIdx,$elapsedText,$checksum,${cfg.fibN},${cfg.yieldEvery}"
      _ <- IO(println(row))
    } yield row

  private def writeCsv(path: String, rows: Vector[String]): IO[Unit] = IO {
    val p = Paths.get(path)
    if (p.getParent != null) Files.createDirectories(p.getParent)
    val needsHeader = !Files.exists(p) || Files.size(p) == 0
    val prefix = if (needsHeader) Header + "\n" else ""
    val content = prefix + rows.mkString("\n") + "\n"
    Files.write(
      p,
      content.getBytes(StandardCharsets.UTF_8),
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND
    )
  }

  def run(cfg: BenchmarkConfig, runBatch: (Int, Int, Int) => IO[Long]): IO[ExitCode] = {
    val warmupTaskCount = cfg.tasks.headOption.getOrElse(1)

    for {
      _ <- if (cfg.warmup) {
        IO(
          println(
            s"warmup runtime=${cfg.runtimeLabel} tasks=$warmupTaskCount fib=${cfg.fibN} yieldEvery=${cfg.yieldEvery}"
          )
        ) *> runBatch(warmupTaskCount, cfg.fibN, cfg.yieldEvery).void
      } else IO.unit
      rows <- cfg.tasks.toList
        .traverse(tasks => (1 to cfg.repeats).toList.traverse(runIdx => timeOne(tasks, runIdx, cfg, runBatch)))
        .map(_.flatten.toVector)
      _ <- writeCsv(cfg.output, rows)
    } yield ExitCode.Success
  }
}
