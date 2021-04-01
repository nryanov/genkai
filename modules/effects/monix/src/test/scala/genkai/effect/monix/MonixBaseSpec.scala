package genkai.effect.monix

import java.util.concurrent.{Executors, ScheduledExecutorService}

import monix.execution.ExecutionModel.AlwaysAsyncExecution
import monix.execution.{Scheduler, UncaughtExceptionReporter}

import scala.concurrent.ExecutionContext

trait MonixBaseSpec {
  val scheduledExecutor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor()
  val executorService: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global
  val uncaughtExceptionReporter: UncaughtExceptionReporter =
    UncaughtExceptionReporter(executorService.reportFailure)

  implicit val scheduler: Scheduler = Scheduler(
    scheduledExecutor,
    executorService,
    uncaughtExceptionReporter,
    AlwaysAsyncExecution
  )

}
