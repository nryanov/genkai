package genkai.effect.zio

import genkai.monad.{MonadError, MonadErrorSpec}
import zio.blocking.Blocking
import zio.{Task, ZIO}

import scala.concurrent.Future

class ZioBlockingMonadErrorSpec extends MonadErrorSpec[Task] with ZioBaseSpec {
  override implicit val monadError: MonadError[Task] =
    runtime.unsafeRun(
      ZIO.service[Blocking.Service].map(blocking => new ZioBlockingMonadError(blocking))
    )

  override def toFuture[A](v: => Task[A]): Future[A] = runtime.unsafeRunToFuture(v)
}
