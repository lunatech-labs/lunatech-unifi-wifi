package modules

import actors.{Cleaner, CleanerScheduler}
import com.google.inject.AbstractModule
import play.api.libs.concurrent.PekkoGuiceSupport

class WifiModule extends AbstractModule with PekkoGuiceSupport {
  override def configure(): Unit = {
    bindActor[Cleaner]("cleaner-actor")
    bind(classOf[CleanerScheduler]).asEagerSingleton()
  }
}
