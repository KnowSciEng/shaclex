package es.weso.mytyping
import cats._, data._
import cats.implicits._

abstract class Typing[Key, Value, Err, Evidence] {

 type Evidences = List[Evidence]

 def hasType(key: Key, value: Value): Boolean =
   getOkValues(key) contains value

 def getValues(key: Key): Map[Value,TypingResult[Err,Evidence]]

 def getOkValues(key: Key): Set[Value]

 def getEvidences(key: Key, value: Value): Option[List[Evidence]]

 def getFailedValues(key: Key): Set[Value]

  def addEvidences(key: Key, value: Value,
      es: List[Evidence]): Typing[Key,Value,Err,Evidence]

  def addEvidence(key: Key, value: Value,
      es: Evidence): Typing[Key,Value,Err,Evidence]

  def addNotEvidence(key: Key, value: Value, e: Err): Typing[Key,Value,Err,Evidence]


  def addType(key:Key, value:Value,
      evidences: List[Evidence] = List()): Typing[Key,Value,Err,Evidence] =
        addEvidences(key,value,evidences)

  def combineTyping(t: Typing[Key,Value,Err,Evidence]): Typing[Key,Value,Err,Evidence]

}

object Typing {

  /**
    *  Creates an empty typing
    **/
  def empty[Key: Show,
    Value: Show,
    Err: Show,
    Evidence: Show
  ]: Typing[Key,Value,Err,Evidence] = {
    val m: Map[Key, Map[Value,TypingResult[Err,Evidence]]] = Map()
    TypingMap(m)
  }

  def combineTypings[
    Key:Show,
    Value: Show,
    Err: Show,
    Evidence: Show](
        ts: Seq[Typing[Key,Value,Err,Evidence]]): Typing[Key,Value,Err,Evidence] = {
    val zero : Typing[Key,Value,Err,Evidence] = Typing.empty
    ts.foldLeft(zero)(_.combineTyping(_))
  }


  implicit def showTyping[
    Key: Show,
    Value: Show,
    Err: Show,
    Evidence:Show] = new Show[Typing[Key,Value,Err,Evidence]] {

    import TypingResult.showTypingResult

    override def show(e: Typing[Key,Value,Err,Evidence]): String =
      e match {
        case tm: TypingMap[Key,Value,Err,Evidence] =>
          tm.m.map{ case (key,valuesMap) => {
      valuesMap.map{ case (value,result) => {
        val showV = (if (result.isOK) "+" else "-" ) + value.show
        s"($key: $showV) -> ${result.show}"
      }}.mkString("\n")
    }}.mkString("\n")
    }
  }

}
