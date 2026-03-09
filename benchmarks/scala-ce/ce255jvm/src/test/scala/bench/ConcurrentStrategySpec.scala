package bench

import cats.effect.IO

class ConcurrentStrategySpec extends munit.FunSuite {

  test("CE2 Concurrent matches Parallel (parallel call: Workload.scala:26, concurrent call: Workload.scala:34)") {
    val parallelChecksum = Workload.runBatchParallel(tasks = 17, fibN = 64, yieldEvery = 8).unsafeRunSync()
    val concurrentChecksum = Workload.runBatchConcurrent(tasks = 17, fibN = 64, yieldEvery = 8).unsafeRunSync()
    assertEquals(concurrentChecksum, parallelChecksum)
  }

  test("CE2 Concurrent uses start/join path (call site: Workload.scala:34)") {
    val singleFib = Workload.fibStackSafe(n = 48, yieldEvery = 8).unsafeRunSync()
    val checksum = Workload.runBatchConcurrent(tasks = 3, fibN = 48, yieldEvery = 8).unsafeRunSync()
    assertEquals(checksum, singleFib)
  }
}
