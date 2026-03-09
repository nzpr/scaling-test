package bench.jmh

import cats.effect.{Concurrent, ContextShift, IO}
import cats.effect.implicits._
import cats.implicits._
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class CE25SchedulerBenchmark {

  @Param(Array("128", "256", "512", "1024", "2048"))
  var tasks: Int = _

  @Param(Array("2048"))
  var fibN: Int = _

  @Param(Array("64"))
  var yieldEvery: Int = _

  private implicit val contextShiftIO: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  private def fibStackSafe(n: Int, yieldEvery: Int): IO[Long] = {
    def loop(i: Int, a: Long, b: Long): IO[Long] =
      if (i <= 0) IO.pure(a)
      else {
        val next = IO.defer(loop(i - 1, b, a + b))
        if (yieldEvery > 0 && i % yieldEvery == 0) IO.shift *> next
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
    implicit val concurrentIO: Concurrent[IO] = IO.ioConcurrentEffect
    Vector
      .fill(tasks)(())
      .traverse(_ => concurrentIO.start(fibStackSafe(fibN, yieldEvery)))
      .flatMap(_.traverse(_.join))
      .map(_.foldLeft(0L)(_ ^ _))
      .unsafeRunSync()
  }
}
