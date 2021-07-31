package examples.cats3

import cats.effect._
import genkai.Strategy
import genkai.redis.redisson.cats3.RedissonCats3AsyncRateLimiter
import org.redisson.config.Config

import scala.concurrent.duration._

object RedissonCats3AsyncRateLimiterExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    RedissonCats3AsyncRateLimiter
      .resource[IO](
        config = {
          val cfg: Config = new Config()
          cfg.useSingleServer().setAddress(s"redis://localhost:6379")
          cfg
        },
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
