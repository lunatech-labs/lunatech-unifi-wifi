package modules

import actors.{Cleaner, CleanerScheduler}
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class WifiModule extends AbstractModule with AkkaGuiceSupport {
  override def configure = {
    bindActor[Cleaner]("cleaner-actor")
    bind(classOf[CleanerScheduler]).asEagerSingleton()
  }
}
