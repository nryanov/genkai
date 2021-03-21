package genkai

trait Key[A] {

  def convert(value: A): String
}

object Key {
  def apply[A](implicit inst: Key[A]): inst.type = inst
}
