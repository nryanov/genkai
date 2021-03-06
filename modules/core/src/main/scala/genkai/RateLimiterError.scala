package genkai

sealed abstract class RateLimiterError(msg: String, cause: Throwable) extends RuntimeException(msg, cause)

final case class RateLimiterClientError(cause: Throwable) extends RateLimiterError(cause.getLocalizedMessage, cause)
