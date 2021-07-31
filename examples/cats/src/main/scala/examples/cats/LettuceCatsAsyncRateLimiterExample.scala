package examples.cats

import cats.effect._
import cats.effect.concurrent.Ref
import genkai.Strategy
import genkai.redis.lettuce.cats.LettuceCatsAsyncRateLimiter

import scala.concurrent.duration._

object LettuceCatsAsyncRateLimiterExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    LettuceCatsAsyncRateLimiter
      .resource[IO](
        redisUri = "redis://localhost:6379",
        // 10 tokens, refill 2 tokens each 5 seconds
        strategy = Strategy.TokenBucket(10, 2, 5.seconds)
      )
      .use { rateLimiter =>
        def acquire(ref: Ref[IO, Long]): IO[Unit] =
          rateLimiter
            .acquireS("key")
            .flatMap { state =>
              if (state.isAllowed)
                ref.updateAndGet(_ + 1L).flatMap(counter => IO.delay(println(s"Counter: $counter")))
              else
                IO.delay(println(s"Wait ${state.resetAfter} seconds")) *> IO.sleep(1.second)
            }
            .flatMap(_ => acquire(ref))

        for {
          ref <- Ref[IO].of(0L)
          _ <- acquire(ref)
        } yield ()
      }
      .as(ExitCode.Success)
}
