package actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem}
import cats.data.EitherT
import cats.implicits._
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import play.api.{Configuration, Environment}
import services.{PeopleService, UnifiService}

import scala.concurrent.ExecutionContext

@Singleton
class CleanerScheduler @Inject()(val system: ActorSystem, @Named("cleaner-actor") val cleanerActor: ActorRef)(implicit ec: ExecutionContext) {
  QuartzSchedulerExtension(system).schedule("Clean", cleanerActor, Clean)
}

class Cleaner @Inject()(configuration: Configuration,
                        environment: Environment,
                        unifiService: UnifiService,
                        peopleService: PeopleService)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  override def receive: Receive = {
    case Clean =>
      log.debug("Going to clean radius users!")

      val oldAccounts = for {
        persons <- EitherT(peopleService.getPersons)
        accounts <- EitherT(unifiService.getRadiusAccounts)
      } yield {
        accounts.map(_.name).toSet diff persons.map(_.email).toSet
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
