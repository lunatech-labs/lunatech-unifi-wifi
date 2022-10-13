package controllers

import com.lunatech.openconnect.{APISessionCookieBaker, Authenticate, GoogleApiSecured}
import play.api.Mode.Prod
import play.api.mvc.{Action, AnyContent, ControllerComponents, InjectedController}
import play.api.{Configuration, Environment}
import services.UnifiService

import javax.inject.Inject
import scala.concurrent.Future

class RestController @Inject()(val configuration: Configuration,
                               val apiSessionCookieBaker: APISessionCookieBaker,
                               override val controllerComponents: ControllerComponents,
                               environment: Environment,
                               auth: Authenticate,
                               unifiService: UnifiService
                              ) extends InjectedController with GoogleApiSecured {

  def authenticate(): Action[AnyContent] = Action.async { request =>
    if (environment.mode == Prod) {
      val body = request.body.asFormUrlEncoded.get
      body("accessToken").headOption match {
        case None => Future(BadRequest("Missing 'accessToken'"))
        case Some(accessToken) => auth.getUserFromToken(accessToken).map {
          case Left(authResult) =>
            val data = Map("email" -> authResult.email, "admin" -> isAdmin(authResult.email).toString)
            Ok(apiSessionCookieBaker.jwtCodec.encode(data))
          case Right(message) =>
            BadRequest(s"Authentication failed, reason: $message").withNewSession
        }
      }
    } else {
      val data = Map("email" -> "developer@lunatech.com", "isAdmin" -> "true")
      Future(Ok(apiSessionCookieBaker.jwtCodec.encode(data)))
    }
  }

  def wifi(): Action[AnyContent] = userAction.async { request =>
    if (environment.mode == Prod) {
      unifiService.createRadiusAccounts(request.email).map {
        case Left(errors) => BadRequest(errors)
        case Right(password) => Ok(password)
      }
    } else {
      Future(Ok("Mock Password"))
    }
  }
}
