package genkai.monad

import scala.concurrent.Future
import scala.util.Try

class TryMonadSpec extends MonadErrorSpec[Try] {
  override implicit val monadError: MonadError[Try] = TryMonadError

  override def toFuture[A](v: => Try[A]): Future[A] = Future.fromTry(v)
}
