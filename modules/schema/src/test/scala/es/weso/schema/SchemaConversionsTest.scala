package es.weso.schema

import org.scalatest._
import cats.implicits._
import es.weso.json.JsonCompare.jsonDiff
import es.weso.rdf.jena.RDFAsJenaModel
import io.circe.parser._

class SchemaConversionsTest extends FunSpec with Matchers with EitherValues {

  describe("ShExC -> ShExJ") {
    val strShExC =
      """
          |prefix : <http://example.org/>
          |:S { :p IRI }
        """.stripMargin

    val strExpected =
      """
          | {
          |   "type" : "Schema",
          |   "@context" : "http://www.w3.org/ns/shex.jsonld",
          |   "shapes" : [
          |     {
          |       "type" : "Shape",
          |       "id" : "http://example.org/S",
          |       "expression" : {
          |         "type" : "TripleConstraint",
          |         "predicate" : "http://example.org/p",
          |         "valueExpr" : {
          |           "type" : "NodeConstraint",
          |           "nodeKind" : "iri"
          |         }
          |       }
          |     }
          |   ]
          |}
        """.stripMargin
    shouldConvert(strShExC, "ShExC", "ShEx", "ShExJ", "ShEx", strExpected, jsonCompare)
  }

  describe(s"ShExC -> Turtle")  {
      val strShExC =
        """
          |prefix : <http://example.org/>
          |:S { :p IRI }
        """.stripMargin

      val strExpected =
        """
          |@prefix :      <http://example.org/> .
          |@prefix sx:      <http://shex.io/ns/shex#> .
          |
          |:S a    sx:Shape ;
          |   sx:closed false ;
          |   sx:expression [
          |    a sx:TripleConstraint ;
          |    sx:predicate :p ;
          |    sx:valueExpr [
          |     a       sx:NodeConstraint ;
          |    sx:nodeKind sx:iri ]
          | ] .
          |
          |[ a  sx:Schema ;
          |  sx:shapes :S
          |] .
        """.stripMargin
      shouldConvert(strShExC, "ShExC", "ShEx", "Turtle", "ShEx", strExpected, rdfCompare)
  }

  describe(s"SHACL (Turtle) -> SHACL (JSON-LD)")  {
    val strShacl =
      """
        |prefix : <http://example.org/>
        |prefix sh: <http://www.w3.org/ns/shacl#>
        |:S a sh:NodeShape ;
        |	sh:nodeKind sh:IRI
      """.stripMargin

    val strExpected =
      """
        |<http://example.org/S> <http://www.w3.org/ns/shacl#nodeKind> <http://www.w3.org/ns/shacl#IRI> .
        |<http://example.org/S> <http://www.w3.org/ns/shacl#closed> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
        |<http://example.org/S> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/ns/shacl#NodeShape> .
      """.stripMargin
    shouldConvert(strShacl, "Turtle", "Shaclex", "N-Triples", "Shaclex", strExpected, rdfCompare)
  }

  describe(s"SHACL (Turtle) -> ShEx (Turtle)")  {
    val strShacl =
      """
        |prefix : <http://example.org/>
        |prefix sh: <http://www.w3.org/ns/shacl#>
        |:S a sh:NodeShape ;
        |	sh:nodeKind sh:IRI
      """.stripMargin

    val strExpected = """
      | { "type" : "Schema",
      |   "@context" : "http://www.w3.org/ns/shex.jsonld",
      |   "shapes" : [ {
      |      "type" : "NodeConstraint",
      |         "id" : "http://example.org/S",
      |         "nodeKind" : "iri"
      |  } ] }
    """.stripMargin
    shouldConvert(strShacl, "Turtle", "Shaclex", "ShExJ", "ShEx", strExpected, jsonCompare)
  }


  def shouldConvert(str: String, format: String, engine: String,
                    targetFormat: String, targetEngine: String,
                    expected: String,
                    compare: (String,String) => Either[String, Boolean]
                   ): Unit = {
   it(s"Should convert $str with format $format and engine $engine and obtain $expected") {
     val r = for {
      schema       <- Schemas.fromString(str, format, engine, None).
        leftMap(e => s"Error reading Schema ($format/$engine): $str\nError: $e")
      strConverted <- schema.convert(Some(targetFormat), Some(targetEngine)).
        leftMap(e => s"Error converting schema(${schema.name}) to ($targetFormat/$targetEngine\n$e")
      result       <- compare(strConverted, expected).
        leftMap(e => s"Error in comparison: $e")
    } yield (strConverted, expected, result)

    r.fold(e => fail(s"Error: $e"), v => {
       val (s1, s2, r) = v
       if (r) {
         info(s"Conversion is ok")
       } else {
         fail(s"Different results\ns1=$s1\ns2$s2")
       }
     })
   }
  }

  def jsonCompare(s1: String, s2: String): Either[String, Boolean] = for {
    json1 <- parse(s1).leftMap(e => s"Error parsing $s1\n$e")
    json2 <- parse(s2).leftMap(e => s"Error parsing $s2\n$e")
    b <-
      if (json1.equals(json2)) { Right(true)}
      else Left(s"Json's different:\nJson1: $json1\nJson2: $json2. Diff: ${jsonDiff(json1, json2)}")
  } yield b

  def rdfCompare(s1: String, s2: String): Either[String, Boolean] = for {
    rdf1 <- RDFAsJenaModel.fromChars(s1,"TURTLE",None)
    rdf2 <- RDFAsJenaModel.fromChars(s2,"TURTLE",None)
    b <- rdf1.isIsomorphicWith(rdf2)
  } yield b

  def shExCompare(s1: String, s2: String): Either[String, Boolean] = for {
    schema1 <- Schemas.fromString(s1,"ShExC","ShEx",None).
      leftMap(e => s"Error reading ShEx from string s1: $s1\n$e")
    schema2 <- Schemas.fromString(s2,"ShExC","ShEx",None).
      leftMap(e => s"Error reading ShEx from string s1: $s1\n$e")
    json1 <- schema1.convert(Some("ShExJ"),Some("ShExC"))
    json2 <- schema2.convert(Some("ShExJ"),Some("ShExC"))
    b <- jsonCompare(json1,json2)
  } yield b

}