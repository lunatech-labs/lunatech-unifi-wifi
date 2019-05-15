package models

import play.api.libs.json.{Format, Json}

case class Person(email: String)

object Person {
  implicit val PersonFormat: Format[Person] = Json.format[Person]
}
