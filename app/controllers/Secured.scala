package controllers

import com.lunatech.openconnect.GoogleSecured
import play.api.mvc.{Request, Result, Results}

trait Secured extends GoogleSecured {
  override def onUnauthorized[A](request: Request[A]): Result = Results.Redirect(routes.UnifiController.login(request.path))
}
