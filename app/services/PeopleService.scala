package services

import cats.data.EitherT
import cats.syntax._
import com.google.inject.Inject
import models.Person
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.ws.{WSAuthScheme, WSClient}
import play.api.{Configuration, Logging}

import scala.concurrent.{ExecutionContext, Future}

class PeopleService @Inject()(configuration: Configuration,
                              ws: WSClient)(implicit ec: ExecutionContext) extends Logging {

  private val query =
    """
      |{
      |  people(employeeStatuses: ACTIVE) {
      |    emailAddress
      |  }
      |}
      |""".stripMargin

  private val baseUrl: String = configuration.get[String]("lunagraph.baseUrl")

  private val clientId: String = configuration.get[String]("lunagraph.client.id")
  private val clientSecret: String = configuration.get[String]("lunagraph.client.secret")
  private val clientIssuer: String = configuration.get[String]("lunagraph.client.issuer")

  def getPersons: Future[Either[String, Seq[Person]]] = {
      for {
        token <- EitherT(getToken())
        people <- EitherT(getPersonsWithToken(token))
      } yield people
  }.value

  private def getPersonsWithToken(token: String): Future[Either[String, Seq[Person]]] = {
    ws.url(s"${baseUrl}/graphql")
      .withHttpHeaders("Authorization" -> s"Bearer $token")
      .post(Json.toJsObject(Map("query" -> query)))
      .map { wsRes =>
        wsRes.status match {
          case Status.OK =>
            val jsonResult = Json.parse(wsRes.body)
            Right((jsonResult \ "data" \ "people").get.as[Seq[Person]])
          case _ => logger.error(s"${wsRes.status} - ${wsRes.body}")
            Left(wsRes.body)
        }
      }
  }


  private def getToken(): Future[Either[String, String]] =
    ws.url(s"${clientIssuer}/protocol/openid-connect/token")
      .withAuth(clientId, clientSecret, WSAuthScheme.BASIC)
      .post(Map("grant_type" -> Seq("client_credentials")))
      .map { clientResponse =>
        clientResponse.status match {
          case Status.OK =>
            val jsonResponse = Json.parse(clientResponse.body)
            val token = (jsonResponse \ "access_token").get.as[String]
            Right(token)
          case _ =>
            logger.error(s"${clientResponse.status} - ${clientResponse.body}")
            Left(clientResponse.body)
        }
      }
}
