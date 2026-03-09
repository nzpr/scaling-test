package bench

import cats.effect.{Concurrent, IO}
import cats.effect.unsafe.implicits.global

class ConcurrentStrategySpec extends munit.FunSuite {

  private implicit val concurrentIO: Concurrent[IO] = IO.asyncForIO

  test("CE3 Concurrent matches Parallel (parallel call: ce370shared/Workload.scala:23, concurrent call: ce370shared/Workload.scala:29)") {
    val parallelChecksum = Workload.runBatchParallel(tasks = 17, fibN = 64, yieldEvery = 8).unsafeRunSync()
    val concurrentChecksum = Workload.runBatchConcurrent(tasks = 17, fibN = 64, yieldEvery = 8).unsafeRunSync()
    assertEquals(concurrentChecksum, parallelChecksum)
  }

  test("CE3 Concurrent uses start/joinWithNever path (call site: ce370shared/Workload.scala:29)") {
    val singleFib = Workload.fibStackSafe(n = 48, yieldEvery = 8).unsafeRunSync()
    val checksum = Workload.runBatchConcurrent(tasks = 3, fibN = 48, yieldEvery = 8).unsafeRunSync()
    assertEquals(checksum, singleFib)
  }
}
