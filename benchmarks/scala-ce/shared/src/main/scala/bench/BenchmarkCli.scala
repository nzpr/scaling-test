package bench

import cats.syntax.either._

import java.util.Locale
import scala.annotation.tailrec

object BenchmarkCli {

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
  def parseArgs(args: List[String], cfg: BenchmarkConfig): Either[String, BenchmarkConfig] =
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
}
