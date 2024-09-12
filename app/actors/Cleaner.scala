package actors

import cats.data.EitherT
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef, ActorSystem}
import org.apache.pekko.extension.quartz.QuartzSchedulerExtension
import services.{PeopleService, UnifiService}

import scala.concurrent.ExecutionContext

@Singleton
class CleanerScheduler @Inject()(val system: ActorSystem, @Named("cleaner-actor") val cleanerActor: ActorRef)(implicit ec: ExecutionContext) {
  QuartzSchedulerExtension(system).schedule("Clean", cleanerActor, Clean)
  cleanerActor ! Clean
}

class Cleaner @Inject()(unifiService: UnifiService,
                        peopleService: PeopleService)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  override def receive: Receive = {
    case Clean =>
      log.debug("Going to clean radius users!")

      val oldAccounts = for {
        persons <- EitherT(peopleService.getPersons)
        accounts <- EitherT(unifiService.getRadiusAccounts)
      } yield {
        accounts.map(_.name).toSet diff persons.map(_.emailAddress).toSet
      }

      oldAccounts.value.map {
        case Right(emails) =>
          emails.foreach { email =>
            log.debug(s"Found $email to delete")
            unifiService.deleteRadiusAccounts(email)
          }
        case Left(error) => log.error(error)
      }
  }
}

case object Clean
