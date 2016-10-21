/* Copyright 2009-2016 EPFL, Lausanne */

package stainless
package frontends.dotc

import dotty.tools.dotc._
import dotty.tools.dotc.typer._
import dotty.tools.dotc.transform._
import dotty.tools.dotc.core.Phases._

class DottyCompiler(inoxCtx: inox.Context) extends Compiler {

  val extraction = new StainlessExtraction(inoxCtx)

  override def phases: List[List[Phase]] = List(
    List(new FrontEnd),
    List(new PostTyper),
    List(new FirstTransform),
    List(extraction)
  )
}