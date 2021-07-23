package genkai.monad

import scala.concurrent.Future
import scala.util.Try

class EitherMonadErrorSpec extends MonadErrorSpec[Either[Throwable, *]] {
  override implicit val monadError: MonadError[Either[Throwable, *]] = EitherMonadError

  override def toFuture[A](v: => Either[Throwable, A]): Future[A] =
    Future.fromTry(Try(v.toTry).flatten)
}
