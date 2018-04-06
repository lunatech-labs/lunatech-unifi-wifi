package services

import com.google.inject.Inject
import models.UnifiSite
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{Format, Json}
import play.api.libs.ws.{WSClient, WSCookie, WSRequest}
import services.UnifiService.{Error, RadiusUser, UnifiResponse, UnifiUser}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class UnifiService @Inject()(configuration: Configuration,
                             ws: WSClient)(implicit ec: ExecutionContext) {
  private val baseUrl: String = configuration.get[String]("unifi.url")
  private val sites = configuration.get[Seq[UnifiSite]]("unifi.sites")

  def createRadiusAccounts(email: String): Future[Either[String, String]] = {
    val password = generatePassword
    forEachSite(password)(site => createRadiusAccount(email, password, site))
  }

  private def createRadiusAccount(email: String, password: String, site: UnifiSite): Future[Either[Error, String]] = {
    withAuth(s"$baseUrl/api/s/${site.id}/rest/account", site) { request =>
      request.post(Json.toJson(RadiusUser(email, password))).map { response =>
        response.status match {
          case Status.OK => Right(password)
          case _ => Left(Error(response.body))
        }
      }
    }
  }

  private def generatePassword: String = Random.alphanumeric.take(32).mkString

  def resetRadiusAccounts(email: String): Future[Either[String, String]] = {
    val newPassword = generatePassword
    forEachSite(newPassword) { site =>
      findAccount(email, site).flatMap {
        case Right(account) => updateRadiusAccount(account.copy(x_password = newPassword), site)
        case Left(error) => Future.successful(Left(error))
      }
    }
  }

  private def updateRadiusAccount(radiusUser: RadiusUser, site: UnifiSite): Future[Either[Error, String]] = {
    radiusUser._id.map { userId =>
      withAuth(s"$baseUrl/api/s/${site.id}/rest/account/$userId", site) { request =>
        request.put(Json.toJson(radiusUser)).map { response =>
          response.status match {
            case Status.OK => Right(radiusUser.x_password)
            case _ => Left(Error(response.body))
          }
        }
      }
    }.getOrElse(Future.successful(Left(Error("User ID missing"))))
  }

  def deleteRadiusAccounts(email: String): Future[Either[String, String]] = {
    forEachSite(s"Your account has been deleted") { site =>
      findAccount(email, site).flatMap {
        case Right(account) => deleteRadiusAccount(account, site)
        case Left(error) => Future.successful(Left(error))
      }
    }
  }

  private def deleteRadiusAccount(radiusUser: RadiusUser, site: UnifiSite): Future[Either[Error, String]] = {
    radiusUser._id.map { userId =>
      withAuth(s"$baseUrl/api/s/${site.id}/rest/account/$userId", site) { request =>
        request.delete.map { response =>
          response.status match {
            case Status.OK => Right(s"${radiusUser.name} deleted")
            case _ => Left(Error(response.body))
          }
        }
      }
    }.getOrElse(Future.successful(Left(Error("User ID missing"))))
  }

  private def withAuth[A](url: String, site: UnifiSite)(process: WSRequest => Future[Either[Error, A]]): Future[Either[Error, A]] = {
    ws.url(s"$baseUrl/api/login")
      .post(Json.toJson(UnifiUser(site.username, site.password)))
      .flatMap { response =>
        response.status match {
          case Status.OK => (response.cookie("csrf_token"), response.cookie("unifises")) match {
            case (Some(token), Some(session)) =>
              process(ws.url(url).withCookies(session).addHttpHeaders("X-Csrf-Token" -> token.value))
            case _ => Future.successful(Left(Error("Missing cookies in auth response")))
          }
          case _ => Future.successful(Left(Error(response.body)))
        }
      }
  }

  private def forEachSite[A](default: A)(process: UnifiSite => Future[Either[Error, A]]): Future[Either[String, A]] = {
    Future.sequence(sites.map(site => process(site))).map { results =>
      results.flatMap(_.swap.toOption) match {
        case Nil => Right(default)
        case errors => Left(errors.map(_.message).mkString("\n"))
      }
    }
  }

  private def findAccount(email: String, site: UnifiSite): Future[Either[Error, RadiusUser]] = {
    withAuth(s"$baseUrl/api/s/${site.id}/rest/account", site) { request =>
      request.get.map { response =>
        response.status match {
          case Status.OK =>
            val error = Error("Account not found, please generate a new account")
            response.json.as[UnifiResponse].data.find(_.name == email).toRight(error)
          case _ => Left(Error(response.body))
        }
      }
    }
  }
}

object UnifiService {

  case class UnifiSession(csrfToken: String, unifiSes: WSCookie)

  case class UnifiUser(username: String, password: String, remember: Boolean = true, strict: Boolean = true)

  case class RadiusUser(name: String, x_password: String, _id: Option[String] = None)

  case class Error(message: String)

  case class UnifiResponse(data: Seq[RadiusUser], meta: Meta)

  case class Meta(msg: Option[String], rc: String)

  implicit private val unifiUserFormat: Format[UnifiUser] = Json.format[UnifiUser]
  implicit private val radiusUserFormat: Format[RadiusUser] = Json.format[RadiusUser]
  implicit private val metaFormat: Format[Meta] = Json.format[Meta]
  implicit private val unifiResponseFormat: Format[UnifiResponse] = Json.format[UnifiResponse]
}
