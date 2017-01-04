package io.example

import dutchman.dsl._
import io.circe.generic.semiauto._

import scala.language.postfixOps

object model {

  case class Person(id: Int, first: String, last: String)

  implicit val personEnc = deriveEncoder[Person]
  implicit val personDec = deriveDecoder[Person]

  implicit val personDocument = new ESDocument[Person] {
    def document(a: Person) = ElasticDocument(a.id.toString, Map(
      "id" → a.id,
      "first" → a.first,
      "last" → a.last
    ))
  }


}
