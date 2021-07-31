package examples

import genkai.Strategy
import genkai.aerospike.AerospikeSyncRateLimiter

import scala.concurrent.duration._

/*
namespace rate-limiter {
  nsup-period 60
  memory-size 1G
  storage-engine memory
}
 */
object AerospikeRateLimiterExample {
  def main(args: Array[String]): Unit = {
    // max tokens = 100, each 5 minutes refill 10 tokens
    val strategy = Strategy.TokenBucket(tokens = 100, refillAmount = 10, refillDelay = 5.minutes)
    val rateLimiter =
      AerospikeSyncRateLimiter.create(
        host = "localhost",
        port = 3000,
        namespace = "rate-limiter",
        strategy = strategy
      )

    var run = true
    try while (run) {
      val state = rateLimiter.acquireS("user")

      if (state.isAllowed) {
        val input = scala.io.StdIn.readLine()
        println(s"Echo: $input")

        if (input == "stop") {
          run = false
        }
      } else {
        Thread.sleep(state.resetAfter.seconds.toMillis)
      }
    } finally rateLimiter.close()
  }
}
