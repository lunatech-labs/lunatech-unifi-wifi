package services

import com.google.inject.Inject
import models.Person
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.ws.{WSAuthScheme, WSClient}
import play.api.{Configuration, Logging}

import java.time.Instant
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

  private var tokenExpiry: Option[Instant] = None
  private var token: Option[String] = None

  def getPersons: Future[Either[String, Seq[Person]]] = for {
    _ <- ensureToken()
    wsRes <- ws.url(s"${baseUrl}/graphql")
      .withHttpHeaders("Authorization"-> s"Bearer ${token.get}")
      .post(Json.toJsObject(Map("query" -> query)))
    res = wsRes.status match {
        case Status.OK =>
          val jsonResult = Json.parse(wsRes.body)
          logger.debug(jsonResult.toString())
          Right((jsonResult \ "data" \ "people").get.as[Seq[Person]])
        case _ => logger.error(wsRes.body)
          Left(wsRes.body)
      }
  } yield res


  private def ensureToken(): Future[Either[String, Unit]] = {
    val cutOff = Instant.now().minusSeconds(30)
    if (token == None || tokenExpiry == None || tokenExpiry.get.isAfter(cutOff)){
        for {
          clientResponse <- ws.url(s"${clientIssuer}/protocol/openid-connect/token")
            .withAuth(clientId, clientSecret, WSAuthScheme.BASIC)
            .post(Map("grant_type" -> Seq("client_credentials")))
          parsedResponse = clientResponse.status match {
            case Status.OK =>
              val jsonResponse = Json.parse(clientResponse.body)
              val expiresIn = (jsonResponse \ "expires_in").get.as[Int]
              token = Some((jsonResponse \ "access_token").get.as[String])
              tokenExpiry = Some(Instant.now().plusSeconds(expiresIn))
              Right(())
            case _ =>
              logger.error(clientResponse.body)
              Left(clientResponse.body)
          }
        } yield parsedResponse
    } else {
      Future.successful(Right(()))
    }
  }
}
