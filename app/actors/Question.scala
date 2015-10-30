package actors

import java.util.UUID
import actors.EchidnaRouter.{Answered, FindMatchExcept}
import actors.Question.{QuestionData, QuestionState}
import actors.User.{OutgoingFailure, OutgoingAnswer, OutgoingQuestion}
import akka.actor.{FSM, Props, ActorRef, Actor}
import scala.concurrent.duration._

class Question(id: UUID, router: ActorRef) extends Actor with FSM[QuestionState, QuestionData] {

  import actors.Question._

  startWith(New, Uninitialized)

  when(New) {
    case Event(Initialize(askerId, asker, content), Uninitialized) => {

      router ! FindMatchExcept(askerId, None)

      goto(SearchingForMatch) using Initialised(askerId, asker, content, None)
    }
  }

  when(SearchingForMatch) {
    case Event(MatchedUser(userId, user), data@Initialised(askerId, asker, content, maybeSentTo)) => {

      user ! OutgoingQuestion(id, askerId, content)

      goto(WaitingForAnswer) using data.copy(alreadySentTo = maybeSentTo match {
        case None => Some(List(userId))
        case Some(usedIds) => Some(userId :: usedIds)
      })
    }
  }

  when(WaitingForAnswer, stateTimeout = 10 seconds) {
    case Event(Answer(value), data@Initialised(_, asker, _, _)) => {

      asker ! OutgoingAnswer(id, value)
      router ! Answered(id)

      goto(Finished) using data
    }

    case Event(StateTimeout, data @ Initialised(askerId, _, _, maybeSentTo)) => {

      router ! FindMatchExcept(askerId, maybeSentTo)

      goto(SearchingForMatch) using data
    }
  }

  when (Finished) {
    case _ => stay()
  }

  whenUnhandled {
    case Event(Timeout, data @ Initialised(_, asker, _, _)) =>

      asker ! OutgoingFailure(id)
      router ! Answered(id)

      goto(Finished) using data

    case Event(unhandled, data) =>
      log.warning("Question {} received unhandled message {}", id, unhandled)
      stay()
  }

  initialize()
}

object Question {

  def props(id: UUID, router: ActorRef) = Props(new Question(id, router))

  sealed trait QuestionState
  case object New extends QuestionState
  case object SearchingForMatch extends QuestionState
  case object WaitingForAnswer extends QuestionState
  case object Finished extends QuestionState

  sealed trait QuestionData
  case object Uninitialized extends QuestionData
  final case class Initialised(askerId: UUID, asker: ActorRef, content: String, alreadySentTo: Option[List[UUID]]) extends QuestionData

  // protocol
  final case class Initialize(askerId: UUID, asker: ActorRef, content: String)
  final case class Answer(value: Int)
  final case class MatchedUser(id: UUID, user: ActorRef)
  case object Timeout
}