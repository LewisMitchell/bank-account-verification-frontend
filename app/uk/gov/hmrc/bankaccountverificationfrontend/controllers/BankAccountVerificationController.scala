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

package uk.gov.hmrc.bankaccountverificationfrontend.controllers

import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{default, ignored, mapping, optional, text}
import play.api.mvc._
import uk.gov.hmrc.bankaccountverificationfrontend.config.AppConfig
import uk.gov.hmrc.bankaccountverificationfrontend.model.BankAccountDetails
import uk.gov.hmrc.bankaccountverificationfrontend.views.html.JourneyStart
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.Future

@Singleton
class BankAccountVerificationController @Inject() (
  appConfig: AppConfig,
  mcc: MessagesControllerComponents,
  startView: JourneyStart
) extends FrontendController(mcc) {

  implicit val config: AppConfig = appConfig

  def start(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      Future.successful(Ok(startView(journeyId, bankAccountDetailsForm)))
    }

  def verifyDetails(journeyId: String) =
    Action.async { implicit request =>
      Future.successful(Ok)
    }

  def bankAccountDetailsForm: Form[BankAccountDetails] =
    Form(
      mapping("name" -> text, "sortCode" -> text, "accountNumber" -> text)(BankAccountDetails.apply)(
        BankAccountDetails.unapply
      )
    )
}
