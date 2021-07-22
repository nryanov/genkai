package genkai.monad

import scala.concurrent.Future

class FutureMonadErrorSpec extends MonadErrorSpec[Future] {
  override implicit val monadError: MonadError[Future] = new FutureMonadAsyncError()

  override def toFuture[A](v: => Future[A]): Future[A] = v
}
