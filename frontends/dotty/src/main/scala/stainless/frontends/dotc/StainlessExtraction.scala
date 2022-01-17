/* Copyright 2009-2021 EPFL, Lausanne */

package stainless
package frontends.dotc

import dotty.tools.dotc._
import core.Names._
import core.Symbols._
import core.Contexts.{Context => DottyContext}
import transform._
import ast.tpd
import ast.Trees._
import typer._

import extraction.xlang.{trees => xt}
import frontend.{CallBack, Frontend, FrontendFactory, ThreadedFrontend, UnsupportedCodeException}

case class ExtractedUnit(file: String, unit: xt.UnitDef, classes: Seq[xt.ClassDef], functions: Seq[xt.FunDef], typeDefs: Seq[xt.TypeDef])

class StainlessExtraction(val inoxCtx: inox.Context) {
  private val symbolMapping = new SymbolMapping

  def extractUnit(using ctx: DottyContext): Option[ExtractedUnit] = {
    // Remark: the method `extractUnit` is called for each compilation unit (which corresponds more or less to a Scala file)
    // Therefore, the symbolMapping instances needs to be shared accross compilation unit.
    // Since `extractUnit` is called within the same thread, we do not need to synchronize accesses to symbolMapping.
    val extraction = new CodeExtraction(inoxCtx, symbolMapping)
    import extraction._

    val unit = ctx.compilationUnit
    val tree = unit.tpdTree
    val (id, stats) = tree match {
      case pd @ PackageDef(refTree, lst) =>
        val id = lst.collectFirst { case PackageDef(ref, stats) => ref } match {
          case Some(ref) => extractRef(ref)
          case None => FreshIdentifier(unit.source.file.name.replaceFirst("[.][^.]+$", ""))
        }
        (id, pd.stats)
      case _ =>
        (FreshIdentifier(unit.source.file.name.replaceFirst("[.][^.]+$", "")), List.empty)
    }

    val fragmentChecker = new FragmentChecker(inoxCtx)
    fragmentChecker.ghostChecker(tree)
    fragmentChecker.checker(tree)

    if (!fragmentChecker.hasErrors()) {
      val (imports, unitClasses, unitFunctions, unitTypeDefs, subs, classes, functions, typeDefs) = {
        // If the user annotates a function with @main, the compiler will generate a top-level class
        // with the same name as the function.
        // This generated class defines def main(args: Array[String]): Unit
        // that just wraps the annotated function in a try-catch.
        // The catch clause intercepts CommandLineParser.ParseError exceptions, causing recovery failure in
        // later Stainless phases so we simply drop that synthetic class.
        val filteredStats = findMain(stats)
          .map(name => stats.filter {
            case TypeDef(n, _) => name != n.toTermName
            case _ => true
          }).getOrElse(stats)
        try
          extraction.extractStatic(filteredStats)
        catch {
          case UnsupportedCodeException(pos, msg) =>
            inoxCtx.reporter.error(pos, msg)
            return None
          case e => throw e
        }
      }
      assert(unitFunctions.isEmpty, "Packages shouldn't contain functions")

      val file = unit.source.file.absolute.path
      val isLibrary = stainless.Main.libraryFiles contains file
      val xtUnit = xt.UnitDef(id, imports, unitClasses, subs, !isLibrary)

      Some(ExtractedUnit(file, xtUnit, classes, functions, typeDefs))
    } else {
      None
    }
  }

  def findMain(stats: List[tpd.Tree])(using DottyContext): Option[TermName] = {
    val trAcc = new tpd.TreeAccumulator[Option[TermName]] {
      override def apply(found: Option[TermName], tree: tpd.Tree)(using DottyContext): Option[TermName] = {
        if (found.isDefined) found
        else tree match {
          case dd@DefDef(nme, _, _, _) if dd.symbol.denot.annotations.exists(_.symbol == defn.MainAnnot) =>
            Some(nme)
          case _ => foldOver(None, tree)
        }
      }
    }
    trAcc(None, stats)
  }
}