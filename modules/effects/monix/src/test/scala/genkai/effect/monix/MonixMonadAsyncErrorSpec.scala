package genkai.effect.monix

import genkai.monad.{MonadError, MonadErrorSpec}
import monix.eval.Task

import scala.concurrent.Future

class MonixMonadAsyncErrorSpec extends MonadErrorSpec[Task] with MonixBaseSpec {
  override implicit val monadError: MonadError[Task] = new MonixMonadAsyncError()

  override def toFuture[A](v: => Task[A]): Future[A] = v.runToFuture
}
