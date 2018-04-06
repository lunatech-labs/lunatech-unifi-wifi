package models

import com.typesafe.config.Config
import play.api.ConfigLoader

import scala.collection.JavaConverters._

case class UnifiSite(id: String, name: String, username: String, password: String)

object UnifiSite {

  implicit val configLoader: ConfigLoader[UnifiSite] = (rootConfig: Config, path: String) =>
    toUnifiSite(rootConfig.getConfig(path))

  implicit val seqConfigLoader: ConfigLoader[Seq[UnifiSite]] = (rootConfig: Config, path: String) =>
    rootConfig.getConfigList(path).asScala.map(toUnifiSite)

  private def toUnifiSite(config: Config) = UnifiSite(
    id = config.getString("id"),
    name = config.getString("name"),
    username = config.getString("username"),
    password = config.getString("password")
  )
}
