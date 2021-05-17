package genkai

import scala.concurrent.duration.Duration

sealed trait ConcurrentStrategy

object ConcurrentStrategy {

  /**
   * @param slots - available slots for concurrent requests
   * @param ttl - default ttl for automatic slot acquisition cleanup if manual cleanup did not succeed
   */
  final case class Default(slots: Long, ttl: Duration) extends ConcurrentStrategy
}
