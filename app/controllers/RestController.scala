package controllers

import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import com.lunatech.openconnect.{Authenticate, GoogleSecured}
import play.api.Mode.Dev
import play.api.{Configuration, Environment}
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}
import services.UnifiService

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RestController @Inject()(val configuration: Configuration,
                               val controllerComponents: ControllerComponents,
                               environment: Environment,
                               auth: Authenticate,
                               unifiService: UnifiService,
                               apiSessionCookieBaker: APISessionCookieBaker
                              )(implicit executionContext: ExecutionContext) extends BaseController with GoogleSecured {

  def authenticate(): Action[AnyContent] = Action.async { request =>
    if(environment.mode == Dev) {
      mockAuth()
    } else {
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
    }
  }

  def wifi(): Action[AnyContent] = userAction.async { request =>
    unifiService.createRadiusAccounts(request.email).map {
      case Left(errors) => BadRequest(errors)
      case Right(password) => Ok(password)
    }
  }

  private def mockAuth() = {
    val email = configuration.get[String]("dev.mockAccount.email")
    val isAdmin = configuration.get[String]("dev.mockAccount.isAdmin")
    val data = Map("email" -> email, "isAdmin" -> isAdmin)
    Future(Ok(apiSessionCookieBaker.jwtCodec.encode(data)))
  }
}
