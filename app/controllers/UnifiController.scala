package controllers

import com.lunatech.openconnect.Authenticate
import javax.inject._
import play.api.mvc.{BaseController, ControllerComponents}
import play.api.{Configuration, Environment, Mode}
import services.UnifiService

import scala.concurrent.ExecutionContext

@Singleton
class UnifiController @Inject()(val configuration: Configuration,
                                val controllerComponents: ControllerComponents,
                                environment: Environment,
                                auth: Authenticate,
                                unifiService: UnifiService)(implicit ec: ExecutionContext) extends BaseController with Secured {

  def index = userAction { implicit request =>
    Ok(views.html.index())
  }

  def wifi = userAction.async { implicit request =>
    unifiService.createRadiusAccounts(request.email).map {
      case Right(password) => Ok(views.html.wifi(request.email, password))
      case Left(errors) => Redirect(routes.UnifiController.index()).flashing("error" -> errors)
    }
  }

  def reset = userAction.async { implicit request =>
    unifiService.resetRadiusAccounts(request.email).map {
      case Right(password) => Ok(views.html.wifi(request.email, password))
      case Left(errors) => Redirect(routes.UnifiController.index()).flashing("error" -> errors)
    }
  }

  def login = Action { implicit request =>
    if (environment.mode == Mode.Prod) {
      val clientId: String = configuration.get[String]("google.clientId")
      val state: String = auth.generateState
      Ok(views.html.login(clientId, state)(request.flash))
    } else {
      Redirect(routes.UnifiController.index()).withSession("email" -> "developer@lunatech.com")
    }
  }

  def authenticate(code: String) = Action.async {
    auth.authenticateToken(code).map {
      case Left(authenticationResult) => Redirect(routes.UnifiController.index()).withSession("email" -> authenticationResult.email)
      case Right(message) => Redirect(routes.UnifiController.login()).withNewSession.flashing("error" -> message.toString())
    }
  }

  def logout = Action {
    Redirect(routes.UnifiController.login()).withNewSession.flashing("success" -> "You've been logged out")
  }
}
