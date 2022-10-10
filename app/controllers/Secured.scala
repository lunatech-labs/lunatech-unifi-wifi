package controllers

import com.google.inject.Inject
import com.lunatech.openconnect.GoogleSecured
import play.api.Configuration
import play.api.http.{SecretConfiguration, SessionConfiguration}
import play.api.mvc.{DefaultJWTCookieDataCodec, DefaultSessionCookieBaker, JWTCookieDataCodec, Request, Result, Results}

import scala.concurrent.duration.FiniteDuration

trait Secured extends GoogleSecured {
  override def onUnauthorized[A](request: Request[A]): Result = Results.Redirect(routes.UnifiController.login(request.path))
}

class APISessionCookieBaker @Inject()(configuration: Configuration,
                                      secretConfiguration: SecretConfiguration,
                                      sessionsConfiguration: SessionConfiguration) extends DefaultSessionCookieBaker {
  private val jwtConf = sessionsConfiguration.jwt.copy(expiresAfter = configuration.get[Option[FiniteDuration]]("session.ttl"))
  override val jwtCodec: JWTCookieDataCodec = DefaultJWTCookieDataCodec(secretConfiguration, jwtConf)
}
