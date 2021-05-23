package genkai

sealed abstract class ConcurrentRateLimiterError(msg: String, cause: Throwable)
    extends RuntimeException(msg, cause)

final case class ConcurrentLimitExhausted[A: Key](key: A)
    extends ConcurrentRateLimiterError(s"No available slots for key: ${Key[A].convert(key)}", null)

final case class ConcurrentRateLimiterClientError(cause: Throwable)
    extends ConcurrentRateLimiterError(cause.getLocalizedMessage, cause)
