package es.weso.shapeMaps
import java.util

import cats._
import cats.data._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import es.weso.rdf.{ Prefix, PrefixMap }
import es.weso.rdf.nodes._
import es.weso.rdf.PREFIXES._
import Parser._
import org.antlr.v4.runtime._
import org.antlr.v4.runtime.tree.ParseTree
import es.weso.shapeMaps.parser.ShapeMapParser.{ StringContext => ShapeMapStringContext, _ }
import es.weso.shapeMaps.parser._

import scala.collection.JavaConverters._

/**
 * Visits the AST and builds the corresponding ShapeMaps classes
 */
class ShapeMapsMaker(
  nodesPrefixMap: PrefixMap,
  shapesPrefixMap: PrefixMap = PrefixMap.empty) extends ShapeMapBaseVisitor[Any] with LazyLogging {

  override def visitShapeMap(ctx: ShapeMapContext): Builder[QueryShapeMap] = for {
    associations <- visitList(visitShapeAssociation, ctx.shapeAssociation())
  } yield QueryShapeMap(associations, nodesPrefixMap, shapesPrefixMap)

  override def visitShapeAssociation(ctx: ShapeAssociationContext): Builder[Association] = for {
    nodeSelector <- visitNodeSelector(ctx.nodeSelector())
    pair <- visitShapeLabel(ctx.shapeLabel())
  } yield {
    val (shapeLabel, info) = pair
    Association(nodeSelector, shapeLabel, info)
  }

  override def visitNodeSelector(ctx: NodeSelectorContext): Builder[NodeSelector] = ctx match {
    case _ if isDefined(ctx.objectTerm()) => for {
      node <- visitObjectTerm(ctx.objectTerm())
    } yield RDFNodeSelector(node)
    case _ if isDefined(ctx.triplePattern()) => visitTriplePattern(ctx.triplePattern())
    case _ => err(s"Internal error visitNodeSelector: unknown ctx $ctx")
  }

  override def visitSubjectTerm(ctx: SubjectTermContext): Builder[RDFNode] = ctx match {
    case _ if isDefined(ctx.iri()) => for {
      iri <- visitIri(ctx.iri(), nodesPrefixMap)
    } yield iri
    case _ if isDefined(ctx.rdfType()) => ok(rdf_type)
  }

  override def visitObjectTerm(ctx: ObjectTermContext): Builder[RDFNode] = ctx match {
    case _ if isDefined(ctx.subjectTerm()) => visitSubjectTerm(ctx.subjectTerm())
    case _ if isDefined(ctx.literal()) => for {
      literal <- visitLiteral(ctx.literal())
    } yield literal
  }

  def visitTriplePattern(ctx: TriplePatternContext): Builder[TriplePattern] = ctx match {
    case s: FocusSubjectContext => for {
      predicate <- visitPredicate(s.predicate())
      objectPattern <- if (isDefined(s.objectTerm())) for {
        obj <- visitObjectTerm(s.objectTerm())
      } yield NodePattern(obj)
      else ok(WildCard)
    } yield TriplePattern(Focus, predicate, objectPattern)
    case s: FocusObjectContext => for {
      predicate <- visitPredicate(s.predicate())
      subjectPattern <- if (isDefined(s.subjectTerm())) for {
        subj <- visitSubjectTerm(s.subjectTerm())
      } yield NodePattern(subj)
      else ok(WildCard)
    } yield TriplePattern(subjectPattern, predicate, Focus)
  }

  override def visitPredicate(ctx: PredicateContext): Builder[IRI] = ctx match {
    case _ if isDefined(ctx.iri()) => visitIri(ctx.iri(), nodesPrefixMap)
    case _ if isDefined(ctx.rdfType()) => ok(rdf_type)
  }

  override def visitShapeLabel(ctx: ShapeLabelContext): Builder[(ShapeMapLabel, Info)] = {
    val info = if (isDefined(ctx.negation())) {
      Info(status = NonConformant)
    } else {
      Info(status = Conformant)
    }
    ctx match {
      case _ if isDefined(ctx.AT_START()) => ok((Start, info))
      case _ if isDefined(ctx.iri()) => for {
        iri <- visitIri(ctx.iri(), shapesPrefixMap)
      } yield (IRILabel(iri), info)
      case _ if isDefined(ctx.KW_START()) => ok((Start, info))
      case _ => err(s"Internal error visitShapeLabel: unknown ctx $ctx")
    }
  }

  override def visitLiteral(ctx: LiteralContext): Builder[Literal] = {
    ctx match {
      case _ if (isDefined(ctx.rdfLiteral)) => visitRdfLiteral(ctx.rdfLiteral())
      case _ if (isDefined(ctx.numericLiteral)) => visitNumericLiteral(ctx.numericLiteral())
      case _ if (isDefined(ctx.booleanLiteral)) => visitBooleanLiteral(ctx.booleanLiteral())
      case _ => err(s"Internal error visitLiteral: unknown ctx $ctx")
    }
  }
  override def visitRdfLiteral(ctx: RdfLiteralContext): Builder[Literal] = {
    val str = visitString(ctx.string())
    /*  Language tagged literals disabled until we fix the grammar (see issue #48
    if (isDefined(ctx.LANGTAG())) {
      // We get the langTag and remove the first character (@)
      val lang = Lang(ctx.LANGTAG().getText().substring(1))
      str.map(s => LangLiteral(s, lang))
    } else */ if (isDefined(ctx.datatype)) {
      for {
        s <- str
        d <- visitDatatype(ctx.datatype(), nodesPrefixMap)
      } yield DatatypeLiteral(s, d)
    } else {
      str.map(StringLiteral(_))
    }
  }

  override def visitNumericLiteral(ctx: NumericLiteralContext): Builder[Literal] = {
    ctx match {
      case _ if (isDefined(ctx.INTEGER())) =>
        ok(IntegerLiteral(Integer.parseInt(ctx.INTEGER().getText)))
      case _ if (isDefined(ctx.DECIMAL())) =>
        ok(DecimalLiteral(BigDecimal(ctx.DECIMAL().getText)))
      case _ if (isDefined(ctx.DOUBLE())) => {
        val str = ctx.DOUBLE().getText
        ok(DoubleLiteral(str.toDouble))
      }
      case _ => err("Unknown ctx in numericLiteral")
    }
  }

  override def visitString(ctx: ShapeMapStringContext): Builder[String] = {
    if (isDefined(ctx.STRING_LITERAL_LONG1())) {
      ok(stripStringLiteralLong1(ctx.STRING_LITERAL_LONG1().getText()))
    } else if (isDefined(ctx.STRING_LITERAL_LONG2())) {
      ok(stripStringLiteralLong2(ctx.STRING_LITERAL_LONG2().getText()))
    } else if (isDefined(ctx.STRING_LITERAL1())) {
      ok(stripStringLiteral1(ctx.STRING_LITERAL1().getText()))
    } else if (isDefined(ctx.STRING_LITERAL2())) {
      ok(stripStringLiteral2(ctx.STRING_LITERAL2().getText()))
    } else
      err(s"visitString: Unknown ctx ${ctx.getClass.getName}")
  }

  def stripStringLiteral1(s: String): String = {
    val regexStr = "\'(.*)\'".r
    s match {
      case regexStr(s) => s
      case _ => throw new Exception(s"stripStringLiteral2 $s doesn't match regex")
    }
  }

  def stripStringLiteral2(s: String): String = {
    val regexStr = "\"(.*)\"".r
    s match {
      case regexStr(s) => s
      case _ => throw new Exception(s"stripStringLiteral2 $s doesn't match regex")
    }
  }

  def stripStringLiteralLong1(s: String): String = {
    val regexStr = "\'\'\'(.*)\'\'\'".r
    s match {
      case regexStr(s) => s
      case _ => throw new Exception(s"stripStringLiteralLong1 $s doesn't match regex")
    }
  }

  def stripStringLiteralLong2(s: String): String = {
    val regexStr = "\"\"\"(.*)\"\"\"".r
    s match {
      case regexStr(s) => s
      case _ => throw new Exception(s"stripStringLiteralLong1 $s doesn't match regex")
    }
  }

  def visitDatatype(ctx: DatatypeContext, prefixMap: PrefixMap): Builder[IRI] = {
    visitIri(ctx.iri(), prefixMap)
  }

  def getBase: Builder[Option[IRI]] = ok(None)

  def visitIri(ctx: IriContext, prefixMap: PrefixMap): Builder[IRI] =
    if (isDefined(ctx.IRIREF())) for {
      base <- getBase
    } yield extractIRIfromIRIREF(ctx.IRIREF().getText, base)
    else for {
      prefixedName <- visitPrefixedName(ctx.prefixedName())
      iri <- resolve(prefixedName, prefixMap)
    } yield iri

  def resolve(prefixedName: String, prefixMap: PrefixMap): Builder[IRI] = {
    val (prefix, local) = splitPrefix(prefixedName)
    logger.info(s"Resolve. prefix: $prefix local: $local Prefixed name: $prefixedName")
    prefixMap.getIRI(prefix) match {
      case None =>
        err(s"Prefix $prefix not found in current prefix map $prefixMap")
      case Some(iri) =>
        ok(iri + local)
    }
  }

  def splitPrefix(str: String): (String, String) = {
    if (str contains ':') {
      val (prefix, name) = str.splitAt(str.lastIndexOf(':'))
      (prefix, name.tail)
    } else {
      ("", str)
    }
  }

  override def visitPrefixedName(ctx: PrefixedNameContext): Builder[String] = {
    ok(ctx.getText())
    /*    ctx match {
          case _ if isDefined(ctx.PNAME_LN()) => ok(ctx.PNAME_LN().getText())
          case _ if isDefined(ctx.PNAME_NS()) => ok(ctx.PNAME_NS().getText())
          case _ => err("visitPrefixedName: Unknown value")
        } */
  }

  /*   override def visitNodeConstraintGroup(ctx: NodeConstraintGroupContext): Builder[ShapeExpr] =
       for {
        shapeOrRef <- visitShapeOrRef(ctx.shapeOrRef())
      } yield shapeOrRef */

  def extractIRIfromIRIREF(d: String, base: Option[IRI]): IRI = {
    val iriRef = "^<(.*)>$".r
    d match {
      case iriRef(i) => {
        // TODO: Check base declaration
        base match {
          case None => IRI(i)
          case Some(b) => b + i
        }
      }
    }
  }

  def getInteger(str: String): Builder[Int] = {
    try {
      ok(str.toInt)
    } catch {
      case e: NumberFormatException =>
        err(s"Cannot get integer from $str")
    }
  }

  def getDecimal(str: String): Builder[BigDecimal] = {
    try {
      ok(BigDecimal(str))
    } catch {
      case e: NumberFormatException =>
        err(s"Cannot get decimal from $str")
    }
  }

  def getDouble(str: String): Builder[Double] = {
    try {
      ok(str.toDouble)
    } catch {
      case e: NumberFormatException =>
        err(s"Cannot get double from $str")
    }
  }

  override def visitBooleanLiteral(ctx: BooleanLiteralContext): Builder[Literal] = {
    if (isDefined(ctx.KW_TRUE()))
      ok(BooleanLiteral(true))
    else
      ok(BooleanLiteral(false))
  }

  def isDefined[A](x: A): Boolean = x != null

  def visitList[A, B](
    visitFn: A => Builder[B],
    ls: java.util.List[A]): Builder[List[B]] = {
    ls.asScala.toList.map(visitFn(_)).sequence
  }

  def visitOpt[A, B](
    visitFn: A => Builder[B],
    v: A): Builder[Option[B]] =
    if (isDefined(v)) visitFn(v).map(Some(_))
    else ok(None)

}