package genkai

import scala.concurrent.duration.Duration

sealed trait Strategy {
  def name: String

  def key[A: Key](key: A): String
}

object Strategy {
  final case class TokenBucket(tokens: Long, refillAmount: Long, refillDelay: Duration)
      extends Strategy {
    override def name: String = "token_bucket"

    override def key[A: Key](key: A): String = Key[A].convert(key)
  }

  final case class FixedWindow(tokens: Long, unit: WindowUnit) extends Strategy {
    override def name: String = "fixed_window"

    override def key[A: Key](key: A): String = Key[A].convert(key)
  }

  final case class SlidingWindow(tokens: Long, unit: WindowUnit) extends Strategy {
    override def name: String = "sliding_window"

    override def key[A: Key](key: A): String = Key[A].convert(key)
  }
}
