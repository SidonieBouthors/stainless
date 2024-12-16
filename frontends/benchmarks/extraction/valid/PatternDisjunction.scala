object PatternDisjunction {
  sealed trait SignSet
  case object None extends SignSet
  case object Any extends SignSet
  case object Neg extends SignSet 
  case object Zer extends SignSet
  case object Pos extends SignSet
  case object NegZer extends SignSet
  case object  NotZer extends SignSet
  case object  PosZer extends SignSet

  def subsetOf(a: SignSet, b: SignSet): Boolean = (a, b) match {
    case (None, _) => true
    case (_, Any) => true
    case (Neg, NegZ | NotZer) => true
    case (Zer, NegZ | PosZer) => true
    case (Pos, NotZ | PosZer) => true
    case _                  => false
  }
}