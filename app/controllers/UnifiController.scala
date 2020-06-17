package controllers

import com.lunatech.openconnect.Authenticate
import javax.inject._
import play.api.mvc.{BaseController, ControllerComponents}
import play.api.{Configuration, Environment, Mode}
import services.UnifiService
import services.UnifiService.Device

import scala.collection.immutable.TreeMap
import scala.concurrent.{ExecutionContext, Future}

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

  def devices(office: String) = userAction.async { implicit request =>
    unifiService.getSite(office).map { site =>
        unifiService.getDevices(site.name).map {
          case Right(devices) =>
            val devicesPerAp = countDevicesPerAP(devices)
            Ok(views.html.devices(devicesPerAp, site))
          case Left(errors) => BadRequest(errors)
        }
    }.getOrElse {
      Future.successful(NotFound("Office not found"))
    }
  }

  private def countDevicesPerAP(devices: Seq[Device]): TreeMap[String, Int] = {
    devices.map { device =>
      val networks = device.vap_table.getOrElse(Nil).groupBy(_.essid)
      val counts = networks.map {
        case (name, nets) => (name, nets.map(_.num_sta).sum)
      }.filterNot {
        case (n, _) => n.contains("Devices") || n.contains("VOIP")
      }
      (device.name, counts.values.sum)
    }.to(TreeMap)
  }

  def login(path: String) = Action { implicit request =>
    if (environment.mode == Mode.Prod) {
      val clientId: String = configuration.get[String]("google.clientId")
      val state: String = auth.generateState
      Ok(views.html.login(clientId, state, path)(request.flash))
    } else {
      Redirect(path).withSession("email" -> "developer@lunatech.com")
    }
  }

  def authenticate(code: String, path: String) = Action.async {
    auth.authenticateToken(code).map {
      case Left(authenticationResult) => Redirect(path).withSession("email" -> authenticationResult.email)
      case Right(message) => Redirect(routes.UnifiController.login(path)).withNewSession.flashing("error" -> message.toString())
    }
  }

  def logout = Action {
    Redirect(routes.UnifiController.login("/")).withNewSession.flashing("success" -> "You've been logged out")
  }
}
