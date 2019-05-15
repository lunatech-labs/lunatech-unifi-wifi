package services

import com.google.inject.Inject
import models.Person
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.Format
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

class PeopleService @Inject()(configuration: Configuration,
                              ws: WSClient)(implicit ec: ExecutionContext) {

  private val baseUrl: String = configuration.get[String]("people.baseUrl")
  private val apiKey: String = configuration.get[String]("people.apiKey")

  def getPersons: Future[Either[String, Seq[Person]]] = {
    withAuth[Seq[Person]](s"$baseUrl/api/people")
  }

  private def withAuth[A](url: String)(implicit fjs: Format[A]): Future[Either[String, A]] = {
    ws.url(url).addQueryStringParameters("apiKey" -> apiKey).get().map { response =>
      response.status match {
        case Status.OK => Right(response.json.as[A])
        case _ => Left(response.body)
      }
    }
  }

}
