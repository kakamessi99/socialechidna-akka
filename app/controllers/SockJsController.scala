package controllers

import actors.{EchidnaRouter, User}
import akka.actor.ActorSystem
import play.api.Play.current
import play.sockjs.api._
import javax.inject._

@Singleton
class SockJsController @Inject() (implicit val system: ActorSystem) extends SockJSRouter {

  val users = system.actorOf(EchidnaRouter.props(system), "echidnaRouter")

  val createUser = User.props(users) _

  def sockjs = SockJS.acceptWithActor[String, String] { request => out =>
    createUser(out)
  }
}
