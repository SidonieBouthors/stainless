object PatternAlternative2 {
  sealed trait Tree
  case class Node(left: Tree, right: Tree) extends Tree
  case class IntLeaf(value: Int) extends Tree
  case class StringLeaf(value: String) extends Tree

  def isBinaryValueLeaf(t: Tree): Boolean = t match {
    case IntLeaf(0 | 1) | StringLeaf("0" | "1") => true
    case _ => false
  }
}