/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bankaccountverification.api

import java.time.ZonedDateTime

import akka.stream.Materializer
import bankaccountverification._
import bankaccountverification.connector.ReputationResponseEnum.{Indeterminate, No, Yes}
import bankaccountverification.web.AccountTypeRequestEnum.{Business, Personal}
import com.codahale.metrics.SharedMetricRegistries
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Configuration, Environment}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents

import scala.concurrent.Future
import scala.concurrent.duration._

class ApiControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite with OptionValues {
  implicit private val timeout: FiniteDuration = 1 second

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)

  private val serviceConfig = new ServicesConfig(configuration)
  private val appConfig = new AppConfig(configuration, serviceConfig)

  override lazy val app = {
    SharedMetricRegistries.clear()
    fakeApplication()
  }

  private lazy val sessionStore = mock[JourneyRepository]

  private val controller =
    new ApiController(appConfig, stubMessagesControllerComponents(), sessionStore)

  implicit val mat = app.injector.instanceOf[Materializer]

  "POST /init" should {
    import InitRequest._

    val newJourneyId = BSONObjectID.generate()

    "return 200" when {
      "A continueUrl is provided" in {
        when(
          sessionStore.create(
            meq(Some("1234")),
            meq("serviceIdentifier"),
            meq("continueUrl"),
            meq(None),
            meq(None),
            meq(Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode")))),
            meq(None)
          )(any())
        ).thenReturn(Future.successful(newJourneyId))

        val json = Json.toJson(InitRequest(Some("1234"), "serviceIdentifier", "continueUrl",
          address = Some(InitRequestAddress(List("Line 1", "Line 2"), Some("Town"), Some("Postcode")))))

        val fakeRequest = FakeRequest("POST", "/api/init").withJsonBody(json)

        val result = controller.init().apply(fakeRequest)
        val initResponse = contentAsJson(result).as[InitResponse]

        status(result) shouldBe Status.OK
        BSONObjectID.parse(initResponse.journeyId).toOption shouldBe Some(newJourneyId)

        initResponse.startUrl shouldBe s"/bank-account-verification/start/${initResponse.journeyId}"
        initResponse.completeUrl shouldBe s"/api/complete/${initResponse.journeyId}"
        initResponse.detailsUrl shouldBe None
      }

      "prepopulated data is provided" in {
        when(
          sessionStore.create(
            meq(Some("1234")),
            meq("serviceIdentifier"),
            meq("continueUrl"),
            meq(None),
            meq(None),
            meq(Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode")))),
            meq(Some(PrepopulatedData(Personal, Some("Bob"), Some("123456"), Some("12345678"), Some("A123"))))
          )(any())
        ).thenReturn(Future.successful(newJourneyId))

        val json = Json.toJson(InitRequest(Some("1234"), "serviceIdentifier", "continueUrl",
          address = Some(InitRequestAddress(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
          prepopulatedData = Some(InitRequestPrepopulatedData(Personal, Some("Bob"), Some("123456"), Some("12345678"), Some("A123")))))

        val fakeRequest = FakeRequest("POST", "/api/init").withJsonBody(json)

        val result = controller.init().apply(fakeRequest)
        val initResponse = contentAsJson(result).as[InitResponse]

        status(result) shouldBe Status.OK
        BSONObjectID.parse(initResponse.journeyId).toOption shouldBe Some(newJourneyId)

        initResponse.startUrl shouldBe s"/bank-account-verification/start/${initResponse.journeyId}"
        initResponse.completeUrl shouldBe s"/api/complete/${initResponse.journeyId}"
        initResponse.detailsUrl shouldBe Some(s"/bank-account-verification/verify/personal/${initResponse.journeyId}")
      }
    }

    "return 400" when {
      "A continueUrl is not provided" in {
        val json = Json.parse("{}")
        val fakeRequest = FakeRequest("POST", "/api/init").withJsonBody(json)

        val result = controller.init().apply(fakeRequest)

        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "return 400" when {
      "Prepopulated data is present but account type is not provided" in {
        val json = Json.parse(
          """{
            |    "serviceIdentifier":"serviceIdentifier",
            |    "continueUrl":"continueUrl",
            |    "address":{"lines":["Line 1","Line 2"],"town":"Town","postcode":"Postcode"},
            |    "prepopulatedData": {
            |        "name": "Bob"
            |    }
            |}""".stripMargin)

        val fakeRequest = FakeRequest("POST", "/api/init").withJsonBody(json)

        val result = controller.init().apply(fakeRequest)

        status(result) shouldBe Status.BAD_REQUEST
      }
    }
  }

  "GET /complete" should {
    "return 200" when {
      "a valid journeyId is provided with a personal response" in {
        val journeyId = BSONObjectID.generate()
        val returnData = Journey(
          journeyId,
          Some("1234"),
          ZonedDateTime.now(),
          "serviceIdentifier",
          "continueUrl",
          Session(
            Some(Personal),
            Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
            Some(
              PersonalSession(Some("Bob"), Some("203040"), Some("12345678"), Some("roll1"), Some(Yes), Some(Yes),
                Some(Indeterminate), Some(No), Some(Indeterminate), Some(Indeterminate), Some(No), Some("sort-code-bank-name-personal"))),
            None
          ))

        when(sessionStore.findById(meq(journeyId), any())(any())).thenReturn(Future.successful(Some(returnData)))

        val fakeCompleteRequest = FakeRequest("GET", s"/api/complete/${journeyId.stringify}")
        val completeResult = controller.complete(journeyId.stringify).apply(fakeCompleteRequest)

        status(completeResult) shouldBe Status.OK
        val json = contentAsJson(completeResult)

        (json \ "accountType").as[String] shouldBe "personal"
        (json \ "personal" \ "accountName").as[String] shouldBe "Bob"
        (json \ "personal" \ "sortCode").as[String] shouldBe "203040"
        (json \ "personal" \ "accountNumber").as[String] shouldBe "12345678"
        (json \ "personal" \ "accountNumberWithSortCodeIsValid").as[String] shouldBe "yes"
        (json \ "personal" \ "accountExists").as[String] shouldBe "yes"
        (json \ "personal" \ "rollNumber").as[String] shouldBe "roll1"
        (json \ "personal" \ "nameMatches").as[String] shouldBe "indeterminate"
        (json \ "personal" \ "addressMatches").as[String] shouldBe "no"
        (json \ "personal" \ "nonConsented").as[String] shouldBe "indeterminate"
        (json \ "personal" \ "subjectHasDeceased").as[String] shouldBe "indeterminate"
        (json \ "personal" \ "nonStandardAccountDetailsRequiredForBacs").as[String] shouldBe "no"
        (json \ "personal" \ "sortCodeBankName").as[String] shouldBe "sort-code-bank-name-personal"
      }

      "a valid journeyId is provided with a business response" in {
        val journeyId = BSONObjectID.generate()
        val returnData = Journey(
          journeyId,
          Some("1234"),
          ZonedDateTime.now(),
          "serviceIdentifier",
          "continueUrl",
          Session(
            Some(Business),
            Some(Address(List("Line 1", "Line 2"), Some("Town"), Some("Postcode"))),
            None,
            Some(
              BusinessSession(Some("Bob Ltd"), Some("203040"), Some("12345678"), Some("roll1"),
                Some(Yes), Some(No), Some(Indeterminate), Some(Indeterminate), Some(Indeterminate), None, Some
                ("sort-code-bank-name-business")))))

        when(sessionStore.findById(meq(journeyId), any())(any()))
          .thenReturn(Future.successful(Some(returnData)))

        val fakeCompleteRequest = FakeRequest("GET", s"/api/complete/${journeyId.stringify}")
        val completeResult = controller.complete(journeyId.stringify).apply(fakeCompleteRequest)

        status(completeResult) shouldBe Status.OK
        val json = contentAsJson(completeResult)

        (json \ "accountType").as[String] shouldBe "business"
        (json \ "business" \ "companyName").as[String] shouldBe "Bob Ltd"
        (json \ "business" \ "sortCode").as[String] shouldBe "203040"
        (json \ "business" \ "accountNumber").as[String] shouldBe "12345678"
        (json \ "business" \ "accountNumberWithSortCodeIsValid").as[String] shouldBe "yes"
        (json \ "business" \ "accountExists").as[String] shouldBe "no"
        (json \ "business" \ "rollNumber").as[String] shouldBe "roll1"
        (json \ "business" \ "companyNameMatches").as[String] shouldBe "indeterminate"
        (json \ "business" \ "companyPostCodeMatches").as[String] shouldBe "indeterminate"
        (json \ "business" \ "companyRegistrationNumberMatches").as[String] shouldBe "indeterminate"
        (json \ "business" \ "sortCodeBankName").as[String] shouldBe "sort-code-bank-name-business"
      }
    }

    "return NotFound" when {
      "a non-existent journeyId is provided" in {
        val nonExistentJourneyId = BSONObjectID.generate()
        when(sessionStore.findById(meq(nonExistentJourneyId), any())(any())).thenReturn(Future.successful(None))

        val fakeCompleteRequest = FakeRequest("GET", s"/api/complete/$nonExistentJourneyId")
        val completeResult = controller.complete(nonExistentJourneyId.stringify).apply(fakeCompleteRequest)
        status(completeResult) shouldBe Status.NOT_FOUND
      }
    }

    "return BadRequest" when {
      "an invalid journeyId is provided" in {
        val nonExistentJourneyId = "invalid-journey-id"
        val fakeCompleteRequest = FakeRequest("GET", s"/api/complete/$nonExistentJourneyId")
        val completeResult = controller.complete(nonExistentJourneyId).apply(fakeCompleteRequest)
        status(completeResult) shouldBe Status.BAD_REQUEST
      }
    }
  }
}
