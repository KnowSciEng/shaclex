package es.weso.shacl

import es.weso.rdf.nodes._
import SHACLPrefixes._

case class ViolationError(
    id: IRI,
    focusNode: RDFNode,
    subject: Option[RDFNode],
    predicate: Option[RDFNode],
    obj: Option[RDFNode],
    message: Option[String],
    sourceConstraint: Option[RDFNode]
) {
  override def toString = s"Violation error on $focusNode: ${message.getOrElse("")}"
}

object ViolationError {

  def basic(suffix: String, focusNode: RDFNode, attempt: Attempt, msg: String) = 
    ViolationError(id = sh + suffix,
      focusNode = focusNode,
      subject = None,
      predicate = attempt.predicate,
      obj = None,
      message = Some(msg + s" Node: ${attempt.node}, Shape: ${attempt.shapeIRI.getOrElse(IRI(""))}, predicate: ${attempt.predicate.getOrElse(IRI(""))}" ),
      sourceConstraint = attempt.shapeIRI)
      
  def failedNodeShape(node: RDFNode, shape: Shape, attempt: Attempt, msg: String) = 
    basic("FailedNodeShape",node, attempt, msg)
      
  def classError(focusNode: RDFNode, cls: RDFNode, attempt: Attempt) = 
    basic("classError", focusNode, attempt, s"Node $focusNode doesn't belong to class $cls")
    
  def datatypeError(focusNode: RDFNode, datatype: RDFNode, attempt: Attempt) = 
    basic("dataTypeError", focusNode, attempt, s"Node $focusNode doesn't have dataType $datatype")
    
  def unsupported(focusNode: RDFNode, attempt: Attempt, msg: String) =  
    basic("unsupported", focusNode, attempt, "Unsupported: " + msg) 
      
  def notNumeric(focusNode: RDFNode, attempt: Attempt) = 
    basic("notNumericError", focusNode, attempt, s"NotNumeric violation. Expected $focusNode to be a number")

  def minExclusiveError(focusNode: RDFNode, attempt: Attempt, n: Int) = 
    basic("minExclusiveError", focusNode, attempt, s"minExclusive violation. Expected $focusNode > $n")
    
  def minInclusiveError(focusNode: RDFNode, attempt: Attempt, n: Int) = 
    basic("minInclusiveError", focusNode, attempt, s"minInclusive violation. Expected $focusNode >= $n")
    
  def maxExclusiveError(focusNode: RDFNode, attempt: Attempt, n: Int) = 
    basic("maxExclusiveError", focusNode, attempt, s"maxExclusive violation. Expected $focusNode < $n")
    
  def maxInclusiveError(focusNode: RDFNode, attempt: Attempt, n: Int) = 
    basic("maxInclusiveError", focusNode, attempt, s"maxInclusive violation. Expected $focusNode <= $n")

  def minLengthError(focusNode: RDFNode, attempt: Attempt, n: Int) = 
    basic("minLengthError", focusNode, attempt, s"minLength violation. Expected length($focusNode) >= $n")

  def maxLengthError(focusNode: RDFNode, attempt: Attempt, n: Int) = 
    basic("maxLengthError", focusNode, attempt, s"maxLength violation. Expected length($focusNode) <= $n")
    
  def patternError(focusNode: RDFNode, attempt: Attempt, p: String, flags: Option[String]) = 
    basic("patternError", focusNode, attempt, s"pattern violation. Expected $focusNode to match '$p'${flags.getOrElse("")}")
    
  def minCountError(focusNode: RDFNode, attempt: Attempt, minCount: Int, count: Int) = 
    basic("minCountError", focusNode, attempt, s"MinCount violation. Expected $minCount, obtained: $count")
        
  def maxCountError(focusNode: RDFNode, attempt: Attempt, maxCount: Int, count: Int) = 
    basic("maxCountError", focusNode, attempt, s"MaxCount violation. Expected $maxCount, obtained: $count")
        
  def iriKindError(focusNode: RDFNode, attempt: Attempt) = 
    basic("iriKindError", focusNode, attempt, s"Node $focusNode is not an IRI")
        
  def literalKindError(focusNode: RDFNode, attempt: Attempt) = 
    basic("literalKindError", focusNode, attempt, s"Node $focusNode is not a Literal")
    
  def bNodeKindError(focusNode: RDFNode, attempt: Attempt) = 
    basic("bNodeKindError", focusNode, attempt, s"Node $focusNode is not a blank node")
    
  def bNodeOrIRIKindError(focusNode: RDFNode, attempt: Attempt) = 
    basic("bNodeOrIRIKindError", focusNode, attempt, s"Node $focusNode is not a blank node or an IRI")
    
  def bNodeOrLiteralKindError(focusNode: RDFNode, attempt: Attempt) = 
    basic("bNodeOrLiteralKindError", focusNode, attempt, s"Node $focusNode is not a blank node or a Literal")
    
  def iriOrLiteralKindError(focusNode: RDFNode, attempt: Attempt) = 
    basic("iriOrLiteralKindError", focusNode, attempt, s"Node $focusNode is not a IRI or a Literal")
    
  def inError(focusNode: RDFNode, attempt: Attempt, values: Seq[Value]) = 
    basic("inError", focusNode, attempt, s"In violation. Expected $focusNode to be in $values")

}
