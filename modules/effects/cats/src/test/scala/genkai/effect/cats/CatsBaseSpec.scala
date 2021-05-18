package genkai.effect.cats

import java.util.concurrent.Executors

import cats.effect.IO

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import cats.effect.Temporal

trait CatsBaseSpec {
  val catsExecutionContext: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
  implicit val cs: ContextShift[IO] = IO.contextShift(catsExecutionContext)
  implicit val timer: Temporal[IO] = IO.timer(catsExecutionContext)
  val blocker: Blocker = Blocker.liftExecutionContext(catsExecutionContext)
}
