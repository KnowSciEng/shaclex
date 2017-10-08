package es.weso.shaclex

import org.rogach.scallop._
import org.rogach.scallop.exceptions._
import com.typesafe.scalalogging._
import es.weso.rdf.nodes.IRI
import es.weso.server._
import es.weso.shacl.converter.RDF2Shacl
import es.weso.utils.JenaUtils
//import org.slf4j.LoggerFactory
// import es.weso.shacl._
import es.weso.schema._
import es.weso.rdf.jena.RDFAsJenaModel
import scala.concurrent.duration._
import es.weso.utils.FileUtils
import scala.util._
import java.nio.file._
import es.weso.rdf.RDFReader
import java.io.File

object Main extends App with LazyLogging {

  override def main(args: Array[String]): Unit = {
    try {
      run(args)
    } catch {
      case (e: Exception) => {
        println(s"Error: ${e.getMessage}")
      }
    }
  }

  def run(args: Array[String]): Unit = {
    val opts = new MainOpts(args, errorDriver)
    opts.verify()

    if (opts.server()) {
      ShaclexServer.main(args)
    }

    val baseFolder: Path = if (opts.baseFolder.isDefined) {
      Paths.get(opts.baseFolder())
    } else {
      Paths.get(".")
    }

    val startTime = System.nanoTime()

    val validateOptions: Either[String, (RDFReader, Schema)] = for {
      rdf <- getRDFReader(opts, baseFolder)
      schema <- getSchema(opts, baseFolder, rdf)
    } yield (rdf, schema)

    validateOptions match {
      case Left(e) => {
        println(s"Error: $e")
      }
      case Right((rdf, schema)) => {
        if (opts.showData()) {
          // If not specified uses the input schema format
          val outDataFormat = opts.outDataFormat.getOrElse(opts.dataFormat())
          println(rdf.serialize(outDataFormat))
        }
        if (opts.showSchema()) {
          // If not specified uses the input schema format
          val outSchemaFormat = opts.outSchemaFormat.getOrElse(opts.schemaFormat())
          schema.serialize(outSchemaFormat) match {
            case Right(str) => println(str)
            case Left(e) => println(s"Error showing schema $schema with format $outSchemaFormat: $e")
          }
        }

        val trigger: String = opts.trigger.toOption.getOrElse(ValidationTrigger.default.name)

        val result = schema.validate(rdf, trigger, "", opts.node.toOption, opts.shapeLabel.toOption,
          rdf.getPrefixMap, schema.pm)

        if (opts.showLog()) {
          logger.info("Show log info = true")
          // TODO...show result
        }

        if (opts.showResult() || opts.outputFile.isDefined) {
          val resultSerialized = result.serialize(opts.resultFormat())
          if (opts.showResult()) println(resultSerialized)
          if (opts.outputFile.isDefined)
            FileUtils.writeFile(opts.outputFile(), resultSerialized)
        }

        if (opts.cnvEngine.isDefined) {
          logger.error("Conversion between engines don't implemented yet")
        }

        if (opts.time()) {
          val endTime = System.nanoTime()
          val time: Long = endTime - startTime
          printTime("Time", opts, time)
        }

      }
    }

  }

  def printTime(msg: String, opts: MainOpts, nanos: Long): Unit = {
    if (opts.time()) {
      val time = Duration(nanos, NANOSECONDS).toMillis
      println(f"$msg%s, $time%10d")
    }
  }

  private def errorDriver(e: Throwable, scallop: Scallop) = e match {
    case Help(s) => {
      println("Help: " + s)
      scallop.printHelp
      sys.exit(0)
    }
    case _ => {
      println("Error: %s".format(e.getMessage))
      scallop.printHelp
      sys.exit(1)
    }
  }

  def getRDFReader(opts: MainOpts, baseFolder: Path): Either[String, RDFReader] = {
    if (opts.data.isDefined) {
      val path = baseFolder.resolve(opts.data())
      for {
        rdf <- RDFAsJenaModel.fromFile(path.toFile(), opts.dataFormat())
      } yield {
        if (opts.inference.isDefined) {
          JenaUtils.inference(rdf.model, opts.inference()) match {
            case Right(model) => RDFAsJenaModel(model)
            case Left(s) => {
              logger.info(s)
              rdf
            }
          }
        } else rdf
      }
    } else {
      logger.info("RDF Data option not specified")
      Right(RDFAsJenaModel.empty)
    }
  }

  def getSchema(opts: MainOpts, baseFolder: Path, rdf: RDFReader): Either[String, Schema] = {
    if (opts.schema.isDefined) {
      val path = baseFolder.resolve(opts.schema())
      val schema = Schemas.fromFile(
        path.toFile(),
        opts.schemaFormat(),
        opts.engine(),
        None)
      schema
    } else {
      logger.info("Schema not specified. Extracting schema from data")
      Schemas.fromRDF(rdf, opts.engine())
    }
  }

}

