package es.weso.shacl

import util._
import es.weso.rdf.RDFBuilder

object Shacl2RDF {
  
  def serialize(shacl:Schema, format: String): Try[String] = {
    Success(s"Not implemented conversion to $format yet\nRaw schema is: $shacl")
    // Failure(throw new Exception("toRDF not implemented yet"))
  }
  
  def toRDF(shacl: Schema): Try[RDFBuilder] = {
    Failure(throw new Exception("toRDF not implemented yet"))
  }
  
}