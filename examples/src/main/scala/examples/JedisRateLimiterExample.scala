package examples

import genkai.redis.jedis.JedisSyncRateLimiter
import genkai.{Strategy, Window}

import scala.concurrent.duration._

object JedisRateLimiterExample {
  def main(args: Array[String]): Unit = {
    // 10 requests per minute
    val strategy = Strategy.FixedWindow(10, Window.Minute)
    // you can also pass the already created client
    val rateLimiter = JedisSyncRateLimiter(host = "localhost", port = 6379, strategy)

    var run = true
    try while (run) {
      // the simplified version is: rateLimiter.acquire(key) -> return true/false
      val state = rateLimiter.acquireS("user")

      if (state.isAllowed) {
        val input = scala.io.StdIn.readLine()
        println(s"Echo: $input")

        if (input == "stop") {
          run = false
        }
      } else {
        println(s"Sleep ${state.resetAfter} seconds")
        Thread.sleep(state.resetAfter.seconds.toMillis)
      }
    } finally rateLimiter.close()
  }
}
