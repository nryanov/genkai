package examples.zio

import zio._
import zio.duration._
import genkai.{Strategy, Window}
import genkai.redis.lettuce.zio.LettuceZioAsyncRateLimiter

object LettuceZioAsyncRateLimiterExample extends App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    LettuceZioAsyncRateLimiter
      .managed(
        "redis://localhost:6379",
        // 10 rps
        Strategy.SlidingWindow(10, Window.Second)
      )
      .use { rateLimiter =>
        def acquire(ref: Ref[Long]) =
          ZIO
            // add non-default cost for each request
            .ifM(rateLimiter.acquire("key", 2))(
              ref.updateAndGet(_ + 1).flatMap(counter => ZIO.effectTotal(println(s"Counter: $counter"))),
              ZIO.effectTotal(println("Sleep")) *> ZIO.sleep(1.second)
            )
            .unit

        for {
          ref <- Ref.make(0L)
          _ <- acquire(ref).forever
        } yield ()
      }
      .exitCode
}
