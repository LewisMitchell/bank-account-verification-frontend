/*
 * Copyright 2020 HM Revenue & Customs
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

package bankaccountverification.connector

import bankaccountverification.connector.ReputationResponseEnum.{No, Yes}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results._
import play.api.routing.sird.{POST => SPOST, _}
import play.api.test.Helpers._
import play.core.server.{Server, ServerConfig}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class BankAccountReputationConnectorSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {

  val barsPort = 11222
  override lazy val app =
    new GuiceApplicationBuilder()
      .configure(
        "bankaccountreputation.validateBankDetails.url" -> s"http://localhost:$barsPort/v2/validateBankDetails"
      )
      .build()

  "Bank account reputation connector" should {

    "handle a 200 response" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/v2/validateBankDetails") => Action(Ok("""{
              |    "accountNumberWithSortCodeIsValid": "yes",
              |    "nonStandardAccountDetailsRequiredForBacs": "no",
              |    "sortCodeIsPresentOnEISCD": "error"
              |}""".stripMargin).withHeaders("Content-Type" -> "application/json"))
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector   = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.validateBankDetails(BarsValidationRequest("203040", "12345678")))
        response shouldBe Success(BarsValidationResponse(Yes, No, None))
      }
    }

    "handle a 200 json response that differs from the expected format" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/v2/validateBankDetails") => Action(Ok("""{
              |    "accountNumberWithSortCodeIsValid": "yes",
              |    "OWAITWHATISTHIS": "no",
              |    "sortCodeIsPresentMEGALOLSOnEISCD": "error"
              |}""".stripMargin).withHeaders("Content-Type" -> "application/json"))
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector   = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.validateBankDetails(BarsValidationRequest("203040", "12345678")))
        response shouldBe a[Failure[_]]
      }
    }

    "handle a 200 non-json response" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/v2/validateBankDetails") =>
            Action(Ok("NOJSON4U").withHeaders("Content-Type" -> "application/json"))
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector   = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.validateBankDetails(BarsValidationRequest("203040", "12345678")))
        response shouldBe a[Failure[_]]
      }
    }

    "handle a 500 response" in {
      Server.withRouterFromComponents(ServerConfig(port = Some(barsPort))) { components =>
        import components.{defaultActionBuilder => Action}
        {
          case SPOST(p"/v2/validateBankDetails") => Action(InternalServerError)
        }
      } { _ =>
        implicit val hc = HeaderCarrier()
        val connector   = app.injector.instanceOf[BankAccountReputationConnector]

        val response = await(connector.validateBankDetails(BarsValidationRequest("203040", "12345678")))
        response shouldBe a[Failure[_]]
      }
    }
  }
}
