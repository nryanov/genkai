package genkai.monad

import genkai.monad.syntax._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future}
import scala.util._

trait MonadErrorSpec[F[_]] extends AsyncFunSuite with Matchers with ScalaFutures {
  implicit val ec: ExecutionContext = ExecutionContext.global

  implicit val monadError: MonadError[F]

  def toFuture[A](v: => F[A]): Future[A]

  test("unit") {
    for {
      result <- toFuture(monadError.unit)
    } yield result shouldBe (())
  }

  test("pure") {
    for {
      result <- toFuture(monadError.pure(1))
    } yield result shouldBe 1
  }

  test("map") {
    for {
      result <- toFuture(monadError.pure(1).map(_ * 2))
    } yield result shouldBe 2
  }

  test("flatMap") {
    for {
      eval <- toFuture(monadError.eval(1 * 2))
      l <- toFuture(monadError.pure(1).flatMap(i => monadError.eval(i * 2)))
      r <- toFuture(monadError.flatMap(monadError.unit)(monadError.pure))

      a1 <- toFuture(
        monadError.pure(1).flatMap(i => monadError.eval(i + 1)).flatMap(i => monadError.eval(i * 2))
      )
      a2 <- toFuture(
        monadError.pure(1).flatMap(i => monadError.eval(i + 1).flatMap(i => monadError.eval(i * 2)))
      )
    } yield {
      eval shouldBe 2
      eval shouldBe l
      r shouldBe (())

      a1 shouldBe 4
      a1 shouldBe a2
    }
  }

  test("raise error") {
    toFuture(monadError.raiseError(new Exception("some error"))).transformWith {
      case Failure(exception) =>
        Future.successful(exception.getLocalizedMessage shouldBe "some error")
      case Success(_) => Future.successful(fail("raiseError unexpectedly completed successfully"))
    }
  }

  test("map error") {
    toFuture(
      monadError
        .raiseError(new Exception("some error"))
        .mapError(_ => new Exception("another error"))
    ).transformWith {
      case Failure(exception) =>
        Future.successful(exception.getLocalizedMessage shouldBe "another error")
      case Success(_) => Future.successful(fail("raiseError unexpectedly completed successfully"))
    }
  }

  test("handle error") {
    for {
      r <- toFuture(
        monadError.raiseError[Boolean](new Exception("should be handled")).handleErrorWith {
          case _: Throwable => monadError.pure(true)
        }
      )
    } yield r shouldBe true
  }

  test("ifM - true") {
    var onTrue = 0
    var onFalse = 0

    for {
      _ <- toFuture(
        monadError.ifM(monadError.pure(true))(
          monadError.eval(onTrue += 1),
          monadError.eval(onFalse += 1)
        )
      )
    } yield {
      onTrue shouldBe 1
      onFalse shouldBe 0
    }
  }

  test("ifM - false") {
    var onTrue = 0
    var onFalse = 0

    for {
      _ <- toFuture(
        monadError.ifM(monadError.pure(false))(
          monadError.eval(onTrue += 1),
          monadError.eval(onFalse += 1)
        )
      )
    } yield {
      onTrue shouldBe 0
      onFalse shouldBe 1
    }
  }

  test("whenA - true") {
    var when = 0

    for {
      _ <- toFuture(monadError.whenA(true)(monadError.eval(when += 1)))
    } yield when shouldBe 1
  }

  test("whenA - false") {
    var when = 0

    for {
      _ <- toFuture(monadError.whenA(false)(monadError.eval(when += 1)))
    } yield when shouldBe 0
  }

  test("guarantee") {
    var count = 0

    for {
      _ <- toFuture(
        monadError
          .guarantee[Unit](monadError.raiseError(new Exception("some error")))(
            monadError.eval(count += 1)
          )
          .handleErrorWith { case _ =>
            monadError.unit
          }
      )
    } yield count shouldBe 1
  }

  test("bracket") {
    var count = 0

    for {
      _ <- toFuture(
        monadError
          .bracket(monadError.unit)(_ => monadError.raiseError[Unit](new Exception("some error")))(
            _ => monadError.eval(count += 1)
          )
          .handleErrorWith { case _ =>
            monadError.unit
          }
      )
    } yield count shouldBe 1
  }
}
