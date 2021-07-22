package genkai.effect.zio

import genkai.monad.{MonadError, MonadErrorSpec}
import zio.Task

import scala.concurrent.Future

class ZioMonadAsyncErrorSpec extends MonadErrorSpec[Task] with ZioBaseSpec {
  override implicit val monadError: MonadError[Task] = new ZioMonadAsyncError()

  override def toFuture[A](v: => Task[A]): Future[A] = runtime.unsafeRunToFuture(v)
}
