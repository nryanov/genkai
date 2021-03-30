package genkai.effect.cats

import java.util.concurrent.Executors

import cats.effect.{Blocker, ContextShift, IO, Timer}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

trait CatsBaseSpec {
  val catsExecutionContext: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
  implicit val cs: ContextShift[IO] = IO.contextShift(catsExecutionContext)
  implicit val timer: Timer[IO] = IO.timer(catsExecutionContext)
  val blocker: Blocker = Blocker.liftExecutionContext(catsExecutionContext)
}
