package services

import com.google.inject.Inject
import models.UnifiSite
import play.api.http.Status
import play.api.libs.json.{Format, Json}
import play.api.libs.ws.{WSClient, WSCookie, WSRequest, WSResponse}
import play.api.{Configuration, Logging}
import services.UnifiService.{Device, Error, RadiusUser, UnifiResponse, UnifiUser}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class UnifiService @Inject()(configuration: Configuration,
                             ws: WSClient)(implicit ec: ExecutionContext) extends Logging {
  private val baseUrl: String = configuration.get[String]("unifi.url")
  private val sites = configuration.get[Seq[UnifiSite]]("unifi.sites")

  def createRadiusAccounts(email: String): Future[Either[String, String]] = {
    val password = generatePassword
    forEachSite(password)(site => createRadiusAccount(email, password, site))
  }

  private def createRadiusAccount(email: String, password: String, site: UnifiSite): Future[Either[Error, String]] = {
    withAuth(s"$baseUrl/api/s/${site.id}/rest/account", site) { request =>
      request.post(Json.toJson(RadiusUser(email, password))).flatMap { response =>
        response.status match {
          case Status.OK => Future.successful(Right(password))
          case _ =>
            response.json.as[UnifiResponse[RadiusUser]].meta.msg match {
              case Some("api.err.DuplicateAccountName") => resetRadiusAccount(email, password, site)
              case _ => Future.successful(Left(handleError(response, site)))
            }
        }
      }
    }
  }

  private def handleError(response: WSResponse, unifiSite: UnifiSite): Error = {
    logger.error(response.body)
    Error("Error: an unexpected error occurred, please try again!")
  }

  private def generatePassword: String = Random.alphanumeric.take(32).mkString

  private def resetRadiusAccount(email: String, newPassword: String, site: UnifiSite): Future[Either[Error, String]] = {
    findAccount(email, site).flatMap {
      case Right(account) => updateRadiusAccount(account.copy(x_password = newPassword), site)
      case Left(error) => Future.successful(Left(error))
    }
  }

  private def updateRadiusAccount(radiusUser: RadiusUser, site: UnifiSite): Future[Either[Error, String]] = {
    radiusUser._id.map { userId =>
      withAuth(s"$baseUrl/api/s/${site.id}/rest/account/$userId", site) { request =>
        request.put(Json.toJson(radiusUser)).map { response =>
          response.status match {
            case Status.OK => Right(radiusUser.x_password)
            case _ => Left(handleError(response, site))
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
            case Status.OK =>
              logger.debug(s"Deleted ${radiusUser.name} at ${site.name}")
              Right(s"${radiusUser.name} deleted")
            case _ => Left(handleError(response, site))
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
          case _ => Future.successful(Left(handleError(response, site)))
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
            response.json.as[UnifiResponse[RadiusUser]].data.find(_.name == email).toRight(error)
          case _ => Left(handleError(response, site))
        }
      }
    }
  }

  private def getAccounts(site: UnifiSite): Future[Either[Error, Seq[RadiusUser]]] = {
    withAuth(s"$baseUrl/api/s/${site.id}/rest/account", site) { request =>
      request.get.map { response =>
        response.status match {
          case Status.OK => Right(response.json.as[UnifiResponse[RadiusUser]].data)
          case _ => Left(handleError(response, site))
        }
      }
    }
  }

  def getRadiusAccounts: Future[Either[String, Seq[RadiusUser]]] = {
    Future.sequence(sites.map(getAccounts)).map { results =>
      results.flatMap(_.swap.toOption) match {
        case Nil => Right(results.flatMap(_.toOption).flatten)
        case errors => Left(errors.map(_.message).mkString("\n"))
      }
    }
  }

  def getDevices(office: String): Future[Either[String, Seq[Device]]] = {
    getSite(office).map(getDevices).map { result =>
      result.map {
        case Right(devices) => Right(devices)
        case Left(error) => Left(error.message)
      }
    }.getOrElse {
      Future.successful(Left("Office not found"))
    }
  }

  def getSite(office: String): Option[UnifiSite] = {
    sites.find(s => s.name.toLowerCase.contains(office.toLowerCase))
  }

  private def getDevices(site: UnifiSite): Future[Either[Error, Seq[Device]]] = {
    withAuth(s"$baseUrl/api/s/${site.id}/stat/device", site) { request =>
      request.get().map { response =>
        response.status match {
          case Status.OK => Right(response.json.as[UnifiResponse[Device]].data.filter(_.`type` == "uap"))
          case _ => Left(handleError(response, site))
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

  case class UnifiResponse[T](data: Seq[T], meta: Meta)

  case class Meta(msg: Option[String], rc: String)

  case class Device(name: String, num_sta: Int, `type`: String, vap_table: Option[Seq[VAP]])

  case class VAP(essid: String, num_sta: Int)

  implicit private val unifiUserFormat: Format[UnifiUser] = Json.format[UnifiUser]
  implicit private val radiusUserFormat: Format[RadiusUser] = Json.format[RadiusUser]
  implicit private val vapFormat: Format[VAP] = Json.format[VAP]
  implicit private val deviceFormat: Format[Device] = Json.format[Device]
  implicit private val metaFormat: Format[Meta] = Json.format[Meta]
  implicit private val unifiResponseRadiusUserFormat: Format[UnifiResponse[RadiusUser]] = Json.format[UnifiResponse[RadiusUser]]
  implicit private val unifiResponseDeviceFormat: Format[UnifiResponse[Device]] = Json.format[UnifiResponse[Device]]
}
