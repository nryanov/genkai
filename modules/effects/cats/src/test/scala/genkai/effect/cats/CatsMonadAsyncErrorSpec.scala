package genkai.effect.cats

import cats.effect.IO
import genkai.monad.{MonadError, MonadErrorSpec}

import scala.concurrent.Future

class CatsMonadAsyncErrorSpec extends MonadErrorSpec[IO] with CatsBaseSpec {
  override implicit val monadError: MonadError[IO] = new CatsMonadAsyncError[IO]()

  override def toFuture[A](v: => IO[A]): Future[A] = v.unsafeToFuture()
}
