package genkai

import org.slf4j.{Logger, LoggerFactory}

trait Logging[F[_]] { self: RateLimiter[F] =>
  protected val logger: Logger = LoggerFactory.getLogger(self.getClass)

  def trace(msg: String): F[Unit] =
    monadError.whenA(logger.isTraceEnabled)(monadError.eval(logger.trace(msg)))

  def trace(msg: String, error: Throwable): F[Unit] =
    monadError.whenA(logger.isTraceEnabled)(monadError.eval(logger.trace(msg, error)))

  def debug(msg: String): F[Unit] =
    monadError.whenA(logger.isDebugEnabled)(monadError.eval(logger.debug(msg)))

  def debug(msg: String, error: Throwable): F[Unit] =
    monadError.whenA(logger.isDebugEnabled)(monadError.eval(logger.debug(msg, error)))

  def info(msg: String): F[Unit] =
    monadError.whenA(logger.isInfoEnabled)(monadError.eval(logger.info(msg)))

  def info(msg: String, error: Throwable): F[Unit] =
    monadError.whenA(logger.isInfoEnabled)(monadError.eval(logger.info(msg, error)))

  def warn(msg: String): F[Unit] =
    monadError.whenA(logger.isWarnEnabled)(monadError.eval(logger.warn(msg)))

  def warn(msg: String, error: Throwable): F[Unit] =
    monadError.whenA(logger.isWarnEnabled)(monadError.eval(logger.warn(msg, error)))

  def error(msg: String): F[Unit] =
    monadError.whenA(logger.isErrorEnabled)(monadError.eval(logger.error(msg)))

  def error(msg: String, error: Throwable): F[Unit] =
    monadError.whenA(logger.isErrorEnabled)(monadError.eval(logger.error(msg, error)))
}
