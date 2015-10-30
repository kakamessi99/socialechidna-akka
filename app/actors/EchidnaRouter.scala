package actors

import java.util.UUID
import actors.User.{Count, InvalidMessage}
import scala.util.Random
import actors.EchidnaRouter.{UsersState, UsersData}
import actors.Question.{Timeout, MatchedUser, Answer, Initialize}
import akka.actor._
import scala.collection.immutable

class EchidnaRouter(val actorSystem: ActorSystem) extends Actor with FSM[UsersState, UsersData] with ActorLogging {

  import actors.EchidnaRouter._
  import scala.concurrent.duration._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  private lazy val random = new Random

  startWith(Active, UsersData(Map.empty, Map.empty))

  when(Active) {
    // new user connected, store in data
    case Event(Connected(id, ref), data@UsersData(users, questions)) => {

      val connectedUsers = users + (id -> ref)

      // send Count message to each user when new user connects
      connectedUsers.foreach(_._2 ! Count(connectedUsers.size))

      stay using data.copy(users = connectedUsers)
    }

    // user disconnected
    case Event(Disconnected(id), data@UsersData(users, questions)) => {

      val connectedUsers = users - id

      // send Count message to each user when user disconnects
      connectedUsers.foreach(_._2 ! Count(connectedUsers.size))

      stay using data.copy(users = connectedUsers)
    }

    // new question arrived
    case Event(IncomingQuestion(askerId, asker, content), data@UsersData(users, questions)) => {

      // only try to find someone to answer the question if user is not alone
      if (users.size > 1) {

        // make new question actor
        val questionId = UUID.randomUUID()
        val question = context.actorOf(Question.props(questionId, self))

        // then initialize it
        question ! Initialize(askerId, asker, content)

        // schedule question timeout
        actorSystem.scheduler.scheduleOnce(1 minute, question, Timeout)

        stay using data.copy(questions = questions + (questionId -> question))
      } else {

        sender() ! InvalidMessage("cannot ask questions when alone", List.empty)

        stay using data
      }
    }

    // new answer arrived
    case Event(IncomingAnswer(questionId, askerId, value), data@UsersData(users, questions)) => {

      questions.find((tuple) => tuple._1 == questionId) match {
        case Some((id, questionRef)) => questionRef ! Answer(value)
        case None => // if question is missing, assume it was answered and this answer is came after timeout
      }

      stay()
    }

    case Event(FindMatchExcept(userId, maybeSentTo), data@UsersData(users, questions)) => {

      val otherUsers = users.keys.toVector.filterNot(id => id == userId)
      val filteredUsers = maybeSentTo match {
        case Some(usedIds) => otherUsers.filterNot(id => usedIds.contains(id))
        case None => otherUsers
      }

      if (filteredUsers.nonEmpty) {
        val matchedUserId = filteredUsers(random.nextInt(filteredUsers.size))

        sender() ! MatchedUser(matchedUserId, users(matchedUserId))
      }

      stay()
    }

    case Event(Answered(questionId), data@UsersData(users, questions)) => {
      sender() ! PoisonPill

      stay using data.copy(questions = questions - questionId)
    }
  }

  whenUnhandled {
    case unhandled =>
      log.warning("Echidna router received unhandled message {}", unhandled)
      stay()
  }

  initialize()
}

object EchidnaRouter {

  def props(actorSystem: ActorSystem) = Props(new EchidnaRouter(actorSystem))

  // States
  sealed trait UsersState
  case object Active extends UsersState

  // Data
  final case class UsersData(users: immutable.Map[UUID, ActorRef], questions: immutable.Map[UUID, ActorRef])

  // Incoming protocol from User incarnations to this actor
  final case class Connected(id: UUID, user: ActorRef)
  final case class Disconnected(id: UUID)
  final case class Answered(id: UUID)
  final case class FindMatchExcept(id: UUID, ignore: Option[List[UUID]])

  sealed trait IncomingMessage
  final case class IncomingQuestion(askerId: UUID, asker: ActorRef, content: String) extends IncomingMessage
  final case class IncomingAnswer(questionId: UUID, askerId: UUID, value: Int) extends IncomingMessage
}
