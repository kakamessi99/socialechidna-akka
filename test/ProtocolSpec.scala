import java.util.UUID

import actors.User._
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import support.UserProtocol

@RunWith(classOf[JUnitRunner])
class ProtocolSpec extends Specification {

  val correctIncomingQuestion = """{"type":"question","payload":{"content":"Will I get rich?"}}"""
  val correctIncomingAnswer = """{"type":"answer","payload":{"question_id":"7f686b8c-a9cf-4dd6-974d-ae4aa808b4a4","asker_id":"52be53c8-f69a-4aad-9b93-040b63b2be6e","value":5}}"""

  val countJson = """{"type":"count","payload":{"connected":1}}"""

  "Protocol" should {
    "be able to parse incoming question message" in {
      UserProtocol.parse(correctIncomingQuestion) must be equalTo Right(Question("Will I get rich?"))
    }

    "be able to parse incoming answer message" in {
      UserProtocol.parse(correctIncomingAnswer) must be equalTo Right(Answer(UUID.fromString("7f686b8c-a9cf-4dd6-974d-ae4aa808b4a4"), UUID.fromString("52be53c8-f69a-4aad-9b93-040b63b2be6e"), 5))
    }

    "be able to cast count message to json" in {
      UserProtocol.write(Count(1)) must be equalTo countJson
    }

    "be able to cast outgoing question to json" in {
      UserProtocol.write(OutgoingQuestion(UUID.fromString("7f686b8c-a9cf-4dd6-974d-ae4aa808b4a4"), UUID.fromString("52be53c8-f69a-4aad-9b93-040b63b2be6e"), "Will I get rich?")) must be equalTo
        """{"type":"question","payload":{"question_id":"7f686b8c-a9cf-4dd6-974d-ae4aa808b4a4","asker_id":"52be53c8-f69a-4aad-9b93-040b63b2be6e","content":"Will I get rich?"}}"""
    }

    "be able to cast outgoing answer to json" in {
      UserProtocol.write(OutgoingAnswer(UUID.fromString("7f686b8c-a9cf-4dd6-974d-ae4aa808b4a4"), 5)) must be equalTo
        """{"type":"answer","payload":{"question_id":"7f686b8c-a9cf-4dd6-974d-ae4aa808b4a4","value":5}}"""
    }
  }

}
