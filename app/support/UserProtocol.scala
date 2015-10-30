package support

import java.util.UUID

import play.api.data.validation.ValidationError

object UserProtocol {

  import actors.User._
  import play.api.libs.json._
  import play.api.libs.json.Reads._
  import play.api.libs.functional.syntax._

  implicit private val answerWrites = new Writes[OutgoingAnswer] {
    def writes(answer: OutgoingAnswer) = Json.obj(
      "type" -> "answer",
      "payload" -> Json.obj(
        "question_id" -> answer.id,
        "value" -> answer.value
      )
    )
  }

  implicit private val questionWrites = new Writes[OutgoingQuestion] {
    def writes(question: OutgoingQuestion) = Json.obj(
      "type" -> "question",
      "payload" -> Json.obj(
        "question_id" -> question.id,
        "asker_id" -> question.askerId,
        "content" -> question.content
      )
    )
  }

  implicit private val failureWrites = new Writes[OutgoingFailure] {
    def writes(failure: OutgoingFailure) = Json.obj(
      "type" -> "failure",
      "payload" -> Json.obj(
        "question_id" -> failure.id
      )
    )
  }

  implicit private val countWrites = new Writes[Count] {
    def writes(count: Count) = Json.obj(
      "type" -> "count",
      "payload" -> Json.obj(
        "connected" -> count.connected
      )
    )
  }

  implicit private val invalidMessageWrites = new Writes[InvalidMessage] {
    def writes(message: InvalidMessage) = Json.obj(
      "type" -> "error",
      "payload" -> Json.obj(
        "message" -> message.error,
        "details" -> message.details
      )
    )
  }

  private val typeReads = (JsPath \ "type").read[String]

  private val questionContentReads = (JsPath \ "payload" \ "content").read[String]

  private val answerReads = (
      (JsPath \ "payload" \ "question_id").read[UUID] and
      (JsPath \ "payload" \ "asker_id").read[UUID] and
      (JsPath \ "payload" \ "value").read[Int](min(1) keepAnd max(20))
    )(Answer.apply _ )

  private val errorsToErrorSeq: PartialFunction[(JsPath, Seq[ValidationError]), String] = {
    case (errorPath, validationErrors) => "invalid value in path: " + errorPath.toString()
  }

  private def checkMessageType(result: JsResult[String]): Either[InvalidMessage, String] = result match {
    case error: JsError => Left(InvalidMessage("missing message type", error.errors.map(errorsToErrorSeq)))
    case success: JsSuccess[String] => Right(success.value)
  }

  private def makeIncomingMessage(messageType: String, parsedMessage: JsValue): Either[InvalidMessage, WebSocketMessage] = {
    messageType match {
      case "question" => parsedMessage.validate[String](questionContentReads) match {
        case error: JsError => Left(InvalidMessage("missing question content", error.errors.map(errorsToErrorSeq)))
        case success: JsSuccess[String] => Right(Question(success.value))
      }
      case "answer" => parsedMessage.validate[Answer](answerReads) match {
        case error: JsError => Left(InvalidMessage("invalid answer message", error.errors.map(errorsToErrorSeq)))
        case success: JsSuccess[Answer] => Right(success.value)
      }
    }
  }

  def write(obj: OutgoingAnswer): String = Json.stringify(Json.toJson(obj))

  def write(obj: OutgoingQuestion): String = Json.stringify(Json.toJson(obj))

  def write(obj: OutgoingFailure): String = Json.stringify(Json.toJson(obj))

  def write(obj: Count): String = Json.stringify(Json.toJson(obj))

  def write(obj: InvalidMessage): String = Json.stringify(Json.toJson(obj))

  def parse(msg: String): Either[InvalidMessage, WebSocketMessage] = {
    try {
      val parsedMessage = Json.parse(msg)

      val msgTypeResult = parsedMessage.validate[String](typeReads)

      checkMessageType(msgTypeResult) match {
        case Left(im: InvalidMessage) => Left(im)
        case Right((s)) => makeIncomingMessage(s, parsedMessage)
      }
    } catch {
      case e: Exception => Left(InvalidMessage("invalid message", List.empty))
    }
  }
}
