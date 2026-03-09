package bench

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.util.Locale
import scala.annotation.tailrec

object BenchmarkMain extends IOApp {

  final case class Config(
      runtimeLabel: String = "ce2.5.5-jvm",
      tasks: Vector[Int] = Vector(512, 1024, 2048, 4096, 8192, 16384),
      fibN: Int = 4096,
      yieldEvery: Int = 64,
      repeats: Int = 3,
      warmup: Boolean = true,
      output: String = "../results/raw/ce2.5.5-jvm.csv"
  )

  final val Header = "runtime,tasks,run,elapsed_ms,checksum,fib_n,yield_every"

  def fibStackSafe(n: Int, yieldEvery: Int): IO[Long] = {
    def loop(i: Int, a: Long, b: Long): IO[Long] = {
      if (i <= 0) IO.pure(a)
      else {
        val next = IO.defer(loop(i - 1, b, a + b))
        if (yieldEvery > 0 && i % yieldEvery == 0) IO.shift *> next
        else next
      }
    }

    loop(n, 0L, 1L)
  }

  def runBatch(tasks: Int, fibN: Int, yieldEvery: Int): IO[Long] =
    Vector
      .fill(tasks)(())
      .parTraverse(_ => fibStackSafe(fibN, yieldEvery))
      .map(_.foldLeft(0L)(_ ^ _))

  def timeOne(tasks: Int, runIdx: Int, cfg: Config): IO[String] =
    for {
      start <- IO(System.nanoTime())
      checksum <- runBatch(tasks, cfg.fibN, cfg.yieldEvery)
      end <- IO(System.nanoTime())
      elapsedMs = (end - start) / 1000000.0
      elapsedText = java.lang.String.format(Locale.US, "%.3f", Double.box(elapsedMs))
      row = s"${cfg.runtimeLabel},$tasks,$runIdx,$elapsedText,$checksum,${cfg.fibN},${cfg.yieldEvery}"
      _ <- IO(println(row))
    } yield row

  def writeCsv(path: String, rows: Vector[String]): IO[Unit] = IO {
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

  def parseTasks(raw: String): Either[String, Vector[Int]] =
    Either
      .catchOnly[NumberFormatException] {
        raw
          .split(",")
          .iterator
          .map(_.trim)
          .filter(_.nonEmpty)
          .map(_.toInt)
          .toVector
      }
      .left
      .map(_ => s"invalid --tasks value: $raw")
      .flatMap { values =>
        if (values.isEmpty) Left("--tasks must not be empty")
        else if (values.exists(_ <= 0)) Left("--tasks values must be > 0")
        else Right(values)
      }

  def parseIntFlag(name: String, raw: String): Either[String, Int] =
    Either.catchOnly[NumberFormatException](raw.toInt).left.map(_ => s"invalid $name value: $raw")

  @tailrec
  def parseArgs(args: List[String], cfg: Config): Either[String, Config] =
    args match {
      case Nil => Right(cfg)
      case "--runtime" :: value :: tail => parseArgs(tail, cfg.copy(runtimeLabel = value))
      case "--tasks" :: value :: tail =>
        parseTasks(value) match {
          case Right(tasks) => parseArgs(tail, cfg.copy(tasks = tasks))
          case Left(err)    => Left(err)
        }
      case "--fib" :: value :: tail =>
        parseIntFlag("--fib", value) match {
          case Right(v) => parseArgs(tail, cfg.copy(fibN = v))
          case Left(e)  => Left(e)
        }
      case "--yield-every" :: value :: tail =>
        parseIntFlag("--yield-every", value) match {
          case Right(v) => parseArgs(tail, cfg.copy(yieldEvery = v))
          case Left(e)  => Left(e)
        }
      case "--repeats" :: value :: tail =>
        parseIntFlag("--repeats", value) match {
          case Right(v) => parseArgs(tail, cfg.copy(repeats = v))
          case Left(e)  => Left(e)
        }
      case "--warmup" :: value :: tail =>
        value.toLowerCase(Locale.ROOT) match {
          case "true"  => parseArgs(tail, cfg.copy(warmup = true))
          case "false" => parseArgs(tail, cfg.copy(warmup = false))
          case _        => Left(s"invalid --warmup value: $value")
        }
      case "--output" :: value :: tail => parseArgs(tail, cfg.copy(output = value))
      case flag :: _                     => Left(s"unknown flag: $flag")
    }

  override def run(args: List[String]): IO[ExitCode] = {
    parseArgs(args, Config()) match {
      case Left(err) =>
        IO(println(s"error: $err")).as(ExitCode.Error)
      case Right(cfg) =>
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
            .traverse(tasks => (1 to cfg.repeats).toList.traverse(runIdx => timeOne(tasks, runIdx, cfg)))
            .map(_.flatten.toVector)
          _ <- writeCsv(cfg.output, rows)
        } yield ExitCode.Success
    }
  }
}
