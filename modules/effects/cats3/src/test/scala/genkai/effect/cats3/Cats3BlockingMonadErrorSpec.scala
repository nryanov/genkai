package genkai.effect.cats3

import cats.effect.IO
import genkai.monad.{MonadError, MonadErrorSpec}

import scala.concurrent.Future

class Cats3BlockingMonadErrorSpec extends MonadErrorSpec[IO] with Cats3BaseSpec {
  override implicit val monadError: MonadError[IO] = new Cats3BlockingMonadError[IO]()

  override def toFuture[A](v: => IO[A]): Future[A] = v.unsafeToFuture()
}
