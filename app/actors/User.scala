package actors

import java.util.UUID

import akka.actor.{ActorLogging, Props, ActorRef, Actor}
import support.UserProtocol

class User(val id: UUID, val router: ActorRef, val out: ActorRef) extends Actor with ActorLogging {

  import actors.EchidnaRouter._
  import actors.User._

  router ! Connected(id, self)
  log.info("New user connected with id {}", id)

  def receive = {
    case msg: String => handleWebSocketMessage(msg)
    case answer: OutgoingAnswer => out ! UserProtocol.write(answer)
    case question: OutgoingQuestion => out ! UserProtocol.write(question)
    case failure: OutgoingFailure => out ! UserProtocol.write(failure)
    case count: Count => out ! UserProtocol.write(count)
    case err: InvalidMessage => out ! UserProtocol.write(err)
    case unhandled => log.warning("User {} received unhandled message: {}", id, unhandled)
  }

  override def postStop() = {
    router ! Disconnected(id)
  }

  def handleWebSocketMessage(msg: String): Unit = {
    UserProtocol.parse(msg) match {
      case Left(err) => out ! UserProtocol.write(err)
      case Right(message) => message match {
        case Question(content) => router ! IncomingQuestion(id, self, content)
        case Answer(questionId, askerId, value) => router ! IncomingAnswer(questionId, askerId, value)
      }
    }
  }
}

object User {

  def props(users: ActorRef)(out: ActorRef) = Props(new User(UUID.randomUUID, users, out))

  final case class InvalidMessage(error: String, details: Seq[String])

  // to actor protocol
  sealed trait OutgoingMessage
  // from Question to asking user
  final case class OutgoingAnswer(id: UUID, value: Int) extends OutgoingMessage
  // from Question to randomly selected user
  final case class OutgoingQuestion(id: UUID, askerId: UUID, content: String) extends OutgoingMessage
  // from Question to asking user
  final case class OutgoingFailure(id: UUID)
  // from Users to everybody
  final case class Count(connected: Int) extends OutgoingMessage


  // internal - from websocket to this actor
  sealed trait WebSocketMessage
  final case class Question(content: String) extends WebSocketMessage
  final case class Answer(questionId: UUID, askerId: UUID, value: Int) extends WebSocketMessage

}
