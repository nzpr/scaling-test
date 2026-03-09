package bench.jmh

import cats.effect.{Concurrent, IO}
import cats.effect.unsafe.implicits.global
import cats.implicits._
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class CE37SchedulerBenchmark {

  @Param(Array("128", "256", "512", "1024", "2048"))
  var tasks: Int = _

  @Param(Array("2048"))
  var fibN: Int = _

  @Param(Array("64"))
  var yieldEvery: Int = _

  private def fibStackSafe(n: Int, yieldEvery: Int): IO[Long] = {
    def loop(i: Int, a: Long, b: Long): IO[Long] =
      if (i <= 0) IO.pure(a)
      else {
        val next = IO.defer(loop(i - 1, b, a + b))
        if (yieldEvery > 0 && i % yieldEvery == 0) IO.cede *> next
        else next
      }

    loop(n, 0L, 1L)
  }

  @Benchmark
  def parallel(): Long =
    Vector
      .fill(tasks)(())
      .parTraverse(_ => fibStackSafe(fibN, yieldEvery))
      .map(_.foldLeft(0L)(_ ^ _))
      .unsafeRunSync()

  @Benchmark
  def concurrent(): Long = {
    val concurrentIO: Concurrent[IO] = IO.asyncForIO
    Vector
      .fill(tasks)(())
      .traverse(_ => concurrentIO.start(fibStackSafe(fibN, yieldEvery)))
      .flatMap(_.traverse(_.joinWithNever))
      .map(_.foldLeft(0L)(_ ^ _))
      .unsafeRunSync()
  }
}
