package controllers

import com.google.inject.Inject
import com.lunatech.openconnect.GoogleSecured
import play.api.Configuration
import play.api.http.{SecretConfiguration, SessionConfiguration}
import play.api.mvc._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class APISessionCookieBaker @Inject()(configuration: Configuration,
                                      secretConfiguration: SecretConfiguration,
                                      sessionsConfiguration: SessionConfiguration) extends DefaultSessionCookieBaker {
  private val jwtConf = sessionsConfiguration.jwt.copy(expiresAfter = configuration.get[Option[FiniteDuration]]("session.ttl"))
  override val jwtCodec: JWTCookieDataCodec = DefaultJWTCookieDataCodec(secretConfiguration, jwtConf)
}

trait ApiSecured extends GoogleSecured {
  val apiSessionCookieBaker: APISessionCookieBaker
  private implicit val executionContext: ExecutionContext = controllerComponents.executionContext
  private val parser: BodyParser[AnyContent] = controllerComponents.parsers.defaultBodyParser

  /**
   * API Actions for authenticated users & admins, respectively
   */
  def apiAction: ApiAction = new ApiAction(parser)

  class ApiRequest[A](val email: String, request: Request[A]) extends WrappedRequest[A](request)

  class ApiAction @Inject()(val parser: BodyParser[AnyContent])(implicit val executionContext: ExecutionContext) extends ActionBuilder[ApiRequest, AnyContent] with ActionRefiner[Request, ApiRequest] {

    def refine[A](request: Request[A]): Future[Either[Result, ApiRequest[A]]] = Future.successful {
      /**
       * We expect a client to send a request with the JWT in the following header:
       * Authorization: Bearer <token>
       */
      request.headers.get("Authorization") match {
        case Some(authHeader) =>
          val decodedToken = apiSessionCookieBaker.jwtCodec.decode(authHeader.replaceFirst("Bearer ", ""))
          decodedToken.get("email") match {
            case Some(email) =>
              if (email.equalsIgnoreCase("0"))
                Left(Results.Unauthorized("Email is invalid"))
              else
                Right(new ApiRequest(email, request))
            case _ => Left(onUnauthorized(request))
          }
        case None => Left(onUnauthorized(request))
      }
    }
  }
}