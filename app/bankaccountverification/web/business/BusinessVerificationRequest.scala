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

package bankaccountverification.web.business

import bankaccountverification.connector.ReputationResponseEnum.{No, Yes}
import bankaccountverification.connector.{BarsBusinessAssessBadRequestResponse, BarsBusinessAssessResponse, BarsBusinessAssessSuccessResponse, ReputationResponseEnum}
import bankaccountverification.web.Forms._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
import play.api.libs.json.Json

case class BusinessVerificationRequest(companyName: String, sortCode: String, accountNumber: String,
                                       rollNumber: Option[String])

object BusinessVerificationRequest {

  object formats {
    implicit val bankAccountDetailsReads = Json.reads[BusinessVerificationRequest]
    implicit val bankAccountDetailsWrites = Json.writes[BusinessVerificationRequest]
  }

  implicit class ValidationFormWrapper(form: Form[BusinessVerificationRequest]) {
    def validateUsingBarsBusinessAssessResponse(response: BarsBusinessAssessResponse): Form[BusinessVerificationRequest] =
      response match {
        case badRequest: BarsBusinessAssessBadRequestResponse =>
          form.fill(form.get).withError("sortCode", "error.sortCode.denyListed")
        case success: BarsBusinessAssessSuccessResponse =>
          import success._
          if (accountNumberWithSortCodeIsValid == No)
            form.fill(form.get).withError("accountNumber", "error.accountNumber.modCheckFailed")
          else if (accountExists == No)
            form.fill(form.get).withError("accountNumber", "error.accountNumber.doesNotExist")
          else if (nonStandardAccountDetailsRequiredForBacs.getOrElse(No) == Yes && form.get.rollNumber.isEmpty)
            form.fill(form.get).withError("rollNumber", "error.rollNumber.required")
          else form
      }
  }

  val form: Form[BusinessVerificationRequest] =
    Form(
      mapping(
        "companyName" -> companyNameMapping,
        "sortCode" -> sortCodeMapping,
        "accountNumber" -> accountNumberMapping,
        "rollNumber" -> optional(rollNumberMapping)
      )(BusinessVerificationRequest.apply)(BusinessVerificationRequest.unapply)
    )

  def companyNameMapping = text.verifying(Constraints.nonEmpty(errorMessage = "error.companyName.required"))
}
