package examples.cats

import cats.effect._
import genkai.{Strategy, Window}
import genkai.redis.jedis.cats.JedisCatsRateLimiter

import scala.concurrent.duration._
import cats.effect.{ Ref, Resource }

object JedisCatsRateLimiterExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Resource.unit[IO]
      .flatMap(blocker =>
        JedisCatsRateLimiter.resource[IO](
          host = "localhost",
          port = 6379,
          // 10 tokens, window 1 minute
          strategy = Strategy.FixedWindow(10, Window.Minute),
          blocker
        )
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
