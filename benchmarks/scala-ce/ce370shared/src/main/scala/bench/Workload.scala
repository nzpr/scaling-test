package bench

import cats.effect.{Concurrent, IO}
import cats.implicits._

object Workload {

  def fibStackSafe(n: Int, yieldEvery: Int): IO[Long] = {
    def loop(i: Int, a: Long, b: Long): IO[Long] =
      if (i <= 0) IO.pure(a)
      else {
        val next = IO.defer(loop(i - 1, b, a + b))
        if (yieldEvery > 0 && i % yieldEvery == 0) IO.cede *> next
        else next
      }

    loop(n, 0L, 1L)
  }

  def runBatchParallel(tasks: Int, fibN: Int, yieldEvery: Int): IO[Long] =
    Vector
      .fill(tasks)(())
      .parTraverse(_ => fibStackSafe(fibN, yieldEvery))
      .map(_.foldLeft(0L)(_ ^ _))

  def runBatchConcurrent(tasks: Int, fibN: Int, yieldEvery: Int)(implicit C: Concurrent[IO]): IO[Long] =
    Vector
      .fill(tasks)(())
      .traverse(_ => C.start(fibStackSafe(fibN, yieldEvery)))
      .flatMap(_.traverse(_.joinWithNever))
      .map(_.foldLeft(0L)(_ ^ _))
}
