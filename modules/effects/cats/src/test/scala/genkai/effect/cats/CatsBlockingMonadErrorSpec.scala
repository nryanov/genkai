package genkai.effect.cats

import cats.effect.IO
import genkai.monad.{MonadError, MonadErrorSpec}

import scala.concurrent.Future

class CatsBlockingMonadErrorSpec extends MonadErrorSpec[IO] with CatsBaseSpec {
  override implicit val monadError: MonadError[IO] = new CatsBlockingMonadError[IO](blocker)

  override def toFuture[A](v: => IO[A]): Future[A] = v.unsafeToFuture()
}
