package genkai.effect.monix

import genkai.monad.MonadAsyncError
import monix.eval.Task

final class MonixMonadAsyncError extends MonadAsyncError[Task] {
  override def async[A](k: (Either[Throwable, A] => Unit) => Unit): Task[A] =
    Task.async { cb =>
      k {
        case left @ Left(_)   => cb(left)
        case right @ Right(_) => cb(right)
      }
    }

  override def cancelable[A](k: (Either[Throwable, A] => Unit) => () => Task[Unit]): Task[A] =
    Task.cancelable { cb =>
      val canceler = k {
        case left @ Left(_)   => cb(left)
        case right @ Right(_) => cb(right)
      }

      canceler()
    }

  override def pure[A](value: A): Task[A] = Task.pure(value)

  override def map[A, B](fa: Task[A])(f: A => B): Task[B] = fa.map(f)

  override def flatMap[A, B](fa: Task[A])(f: A => Task[B]): Task[B] = fa.flatMap(f)

  override def raiseError[A](error: Throwable): Task[A] = Task.raiseError(error)

  override def adaptError[A](fa: Task[A])(pf: PartialFunction[Throwable, Throwable]): Task[A] =
    fa.onErrorHandleWith {
      case err: Throwable if pf.isDefinedAt(err) => Task.raiseError(pf(err))
      case err                                   => Task.raiseError(err)

    }

  override def mapError[A](fa: Task[A])(f: Throwable => Throwable): Task[A] =
    fa.onErrorHandleWith(err => Task.raiseError(f(err)))

  override def handleError[A](fa: Task[A])(pf: PartialFunction[Throwable, A]): Task[A] =
    fa.onErrorRecover(pf)

  override def handleErrorWith[A](fa: Task[A])(pf: PartialFunction[Throwable, Task[A]]): Task[A] =
    fa.onErrorRecoverWith(pf)

  override def ifA[A](fcond: Task[Boolean])(ifTrue: => Task[A], ifFalse: => Task[A]): Task[A] =
    fcond.flatMap { flag =>
      if (flag) ifTrue
      else ifFalse
    }

  override def whenA[A](cond: Boolean)(f: => Task[A]): Task[Unit] = Task.when(cond)(f.void)

  override def void[A](fa: Task[A]): Task[Unit] = fa.void

  override def eval[A](f: => A): Task[A] = Task.eval(f)

  override def guarantee[A](f: Task[A])(g: => Task[Unit]): Task[A] = f.guarantee(g)

  override def unit: Task[Unit] = Task.unit

  override def suspend[A](fa: => Task[A]): Task[A] = Task.suspend(fa)

  override def flatten[A](fa: Task[Task[A]]): Task[A] = fa.flatten
}
