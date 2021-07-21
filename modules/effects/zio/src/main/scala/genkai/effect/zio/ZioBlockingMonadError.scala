package genkai.effect.zio

import zio._
import zio.blocking._
import genkai.monad.MonadError

final class ZioBlockingMonadError(blocking: Blocking.Service) extends MonadError[Task] {
  override def pure[A](value: A): Task[A] = Task.succeed(value)

  override def map[A, B](fa: => Task[A])(f: A => B): Task[B] = fa.map(f)

  override def flatMap[A, B](fa: => Task[A])(f: A => Task[B]): Task[B] = fa.flatMap(f)

  override def tap[A, B](fa: => Task[A])(f: A => Task[B]): Task[A] = fa.tap(f)

  override def raiseError[A](error: Throwable): Task[A] = Task.fail(error)

  override def adaptError[A](fa: => Task[A])(pf: PartialFunction[Throwable, Throwable]): Task[A] =
    fa.mapError {
      case err: Throwable if pf.isDefinedAt(err) => pf(err)
      case err                                   => err
    }

  override def mapError[A](fa: => Task[A])(f: Throwable => Throwable): Task[A] =
    fa.mapError(f)

  override def handleError[A](fa: => Task[A])(pf: PartialFunction[Throwable, A]): Task[A] =
    fa.catchSome {
      case err: Throwable if pf.isDefinedAt(err) => Task.effect(pf(err))
      case err                                   => Task.fail(err)
    }

  override def handleErrorWith[A](fa: => Task[A])(
    pf: PartialFunction[Throwable, Task[A]]
  ): Task[A] =
    fa.catchSome(pf)

  override def ifM[A](fcond: => Task[Boolean])(ifTrue: => Task[A], ifFalse: => Task[A]): Task[A] =
    Task.ifM(fcond)(ifTrue, ifFalse)

  override def whenA[A](cond: Boolean)(f: => Task[A]): Task[Unit] =
    Task.when(cond)(f)

  override def void[A](fa: => Task[A]): Task[Unit] = fa.unit

  override def eval[A](f: => A): Task[A] = blocking.effectBlocking(f)

  override def unit: Task[Unit] = Task.unit

  override def suspend[A](fa: => Task[A]): Task[A] = Task.effectSuspend(fa)

  override def flatten[A](fa: => Task[Task[A]]): Task[A] = Task.flatten(fa)

  override def guarantee[A](f: => Task[A])(g: => Task[Unit]): Task[A] =
    f.ensuring(g.ignore)

  override def bracket[A, B](acquire: => Task[A])(use: A => Task[B])(
    release: A => Task[Unit]
  ): Task[B] =
    ZIO.bracket(acquire, (a: A) => release(a).orDie, use)
}
