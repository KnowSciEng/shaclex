package es.weso.shex

import es.weso.rdf.nodes.IRI
import values._

abstract sealed trait TripleExpr {
  def addId(label: ShapeLabel): TripleExpr
  def id: Option[ShapeLabel]
  def paths(schema: Schema): List[Path]
}

case class EachOf( id: Option[ShapeLabel],
                   expressions: List[TripleExpr],
                   optMin: Option[Int],
                   optMax: Option[Max],
                   semActs: Option[List[SemAct]],
                   annotations: Option[List[Annotation]]) extends TripleExpr {
  lazy val min = optMin.getOrElse(Cardinality.defaultMin)
  lazy val max = optMax.getOrElse(Cardinality.defaultMax)
  override def addId(lbl: ShapeLabel) = this.copy(id = Some(lbl))

  override def paths(schema: Schema): List[Path] = expressions.map(_.paths(schema)).flatten

}

object EachOf {
  def fromExpressions(es: TripleExpr*): EachOf =
    EachOf(None,es.toList,None,None,None,None)
}

case class OneOf(
                  id: Option[ShapeLabel],
                  expressions: List[TripleExpr],
                  optMin: Option[Int],
                  optMax: Option[Max],
                  semActs: Option[List[SemAct]],
                  annotations: Option[List[Annotation]]) extends TripleExpr {
  lazy val min = optMin.getOrElse(Cardinality.defaultMin)
  lazy val max = optMax.getOrElse(Cardinality.defaultMax)
  override def addId(lbl: ShapeLabel) = this.copy(id = Some(lbl))

  override def paths(schema:Schema): List[Path] = expressions.map(_.paths(schema)).flatten
}

object OneOf {
  def fromExpressions(es: TripleExpr*): OneOf =
    OneOf(None,es.toList,None,None,None,None)
}

case class Inclusion(include: ShapeLabel) extends TripleExpr {
  override def addId(lbl: ShapeLabel) = this
  override def id = None

  override def paths(schema: Schema): List[Path] = {
    schema.getTripleExpr(include).map(_.paths(schema)).getOrElse(List())
  }

}

case class TripleConstraint(
                             id: Option[ShapeLabel],
                             optInverse: Option[Boolean],
                             optNegated: Option[Boolean],
                             predicate: IRI,
                             valueExpr: Option[ShapeExpr],
                             optMin: Option[Int],
                             optMax: Option[Max],
                             optVariableDecl: Option[VarName],
                             semActs: Option[List[SemAct]],
                             annotations: Option[List[Annotation]]) extends TripleExpr {
  lazy val inverse = optInverse.getOrElse(false)
  lazy val direct = !inverse
  lazy val negated = optNegated.getOrElse(false)
  lazy val min = optMin.getOrElse(Cardinality.defaultMin)
  lazy val max = optMax.getOrElse(Cardinality.defaultMax)
  lazy val path: Path =
    if (direct) Direct(predicate)
    else Inverse(predicate)
  override def addId(lbl: ShapeLabel) = this.copy(id = Some(lbl))
  override def paths(schema: Schema): List[Path] = List(path)

  def decreaseCard: TripleConstraint = this.copy(
    optMin = optMin.map(x => Math.min(x - 1,0)),
    optMax = optMax.map(_.decreaseCard)
  )

}

/**
  * Support for arithmetic expressions
  * @param id
  * @param e
  */
case class Expr(id: Option[ShapeLabel],
                e: ValueExpr
               ) extends TripleExpr {
  def addId(label: ShapeLabel) = this.copy(id = Some(label))
  override def paths(schema: Schema): List[Path] = List()
}

object TripleConstraint {
  def emptyPred(pred: IRI): TripleConstraint =
    TripleConstraint(
      None, None, None, pred, None, None, None, None, None, None)

  def valueExpr(pred: IRI, ve: ShapeExpr): TripleConstraint =
    emptyPred(pred).copy(valueExpr = Some(ve))

  def datatype(pred: IRI, iri: IRI, facets: List[XsFacet]): TripleConstraint =
    emptyPred(pred).copy(valueExpr = Some(NodeConstraint.datatype(iri, facets)))
}

