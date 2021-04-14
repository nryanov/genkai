package genkai

import java.util.UUID

/**
 * Type class. Used to generate object id.
 * @tparam A - key type
 */
trait Key[A] { self =>

  /**
   * @param value - value which will be used as unique (or not) identifier
   * @return - string representation of id
   */
  def convert(value: A): String

  final def contramap[B](f: B => A): Key[B] = new Key[B] {
    override def convert(value: B): String = self.convert(f(value))
  }
}

object Key {
  def apply[A](implicit inst: Key[A]): inst.type = inst

  implicit val byteKeyInstance: Key[Byte] = new Key[Byte] {
    override def convert(value: Byte): String = value.toString
  }

  implicit val shortKeyInstance: Key[Short] = new Key[Short] {
    override def convert(value: Short): String = value.toString
  }

  implicit val intKeyInstance: Key[Int] = new Key[Int] {
    override def convert(value: Int): String = value.toString
  }

  implicit val longKeyInstance: Key[Long] = new Key[Long] {
    override def convert(value: Long): String = value.toString
  }

  implicit val floatKeyInstance: Key[Float] = new Key[Float] {
    override def convert(value: Float): String = value.toString
  }

  implicit val doubleKeyInstance: Key[Double] = new Key[Double] {
    override def convert(value: Double): String = value.toString
  }

  implicit val charKeyInstance: Key[Char] = new Key[Char] {
    override def convert(value: Char): String = value.toString
  }

  implicit val stringKeyInstance: Key[String] = new Key[String] {
    override def convert(value: String): String = value
  }

  implicit val bigIntKeyInstance: Key[BigInt] = new Key[BigInt] {
    override def convert(value: BigInt): String = value.toString()
  }

  implicit val bigDecimalKeyInstance: Key[BigDecimal] = new Key[BigDecimal] {
    override def convert(value: BigDecimal): String = value.toString()
  }

  implicit val uuidKeyInstance: Key[UUID] = new Key[UUID] {
    override def convert(value: UUID): String = value.toString
  }
}
