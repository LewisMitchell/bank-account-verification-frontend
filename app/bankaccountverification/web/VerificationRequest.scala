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

package bankaccountverification.web

import bankaccountverification.connector.ReputationResponseEnum.{No, Yes}
import bankaccountverification.connector.{BarsBusinessAssessResponse, BarsPersonalAssessResponse, BarsValidationResponse, ReputationResponseEnum}
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
import play.api.libs.json.Json

import scala.util.{Failure, Success, Try}

case class PersonalVerificationRequest(
  accountName: String,
  sortCode: String,
  accountNumber: String,
  rollNumber: Option[String] = None
)

object PersonalVerificationRequest extends VerificationRequestBase {
  object formats {
    implicit val bankAccountDetailsReads  = Json.reads[PersonalVerificationRequest]
    implicit val bankAccountDetailsWrites = Json.writes[PersonalVerificationRequest]
  }

  implicit class ValidationFormWrapper(form: Form[PersonalVerificationRequest]) {

    def validateUsingBarsValidateResponse(response: BarsValidationResponse): Form[PersonalVerificationRequest] =
      validate(response.accountNumberWithSortCodeIsValid, response.nonStandardAccountDetailsRequiredForBacs)

    def validateUsingBarsPersonalAssessResponse(
      response: BarsPersonalAssessResponse
    ): Form[PersonalVerificationRequest] =
      validate(
        response.accountNumberWithSortCodeIsValid,
        response.nonStandardAccountDetailsRequiredForBacs.getOrElse(No),
        Some(response.accountExists)
      )

    private def validate(
      accountNumberWithSortCodeIsValid: ReputationResponseEnum,
      nonStandardAccountDetailsRequiredForBacs: ReputationResponseEnum,
      accountExists: Option[ReputationResponseEnum] = None
    ): Form[PersonalVerificationRequest] =
      if (accountNumberWithSortCodeIsValid == No)
        form
          .fill(form.get)
          .withError("accountNumber", "error.accountNumber.modCheckFailed")
      else if (accountExists.isDefined && accountExists.get == No)
        form
          .fill(form.get)
          .withError("accountNumber", "error.accountNumber.doesNotExist")
      else if (nonStandardAccountDetailsRequiredForBacs == Yes && form.get.rollNumber.isEmpty)
        form
          .fill(form.get)
          .withError("rollNumber", "error.rollNumber.required")
      else form

  }

  val form: Form[PersonalVerificationRequest] =
    Form(
      mapping(
        "accountName"   -> accountNameMapping,
        "sortCode"      -> sortCodeMapping,
        "accountNumber" -> accountNumberMapping,
        "rollNumber"    -> optional(rollNumberMapping)
      )(PersonalVerificationRequest.apply)(PersonalVerificationRequest.unapply)
    )

  def accountNameMapping = text.verifying(Constraints.nonEmpty(errorMessage = "error.accountName.required"))
}

case class BusinessVerificationRequest(
  companyName: String,
  companyRegistrationNumber: Option[String],
  sortCode: String,
  accountNumber: String,
  rollNumber: Option[String]
)

object BusinessVerificationRequest extends VerificationRequestBase {
  object formats {
    implicit val bankAccountDetailsReads  = Json.reads[BusinessVerificationRequest]
    implicit val bankAccountDetailsWrites = Json.writes[BusinessVerificationRequest]
  }

  implicit class ValidationFormWrapper(form: Form[BusinessVerificationRequest]) {

    def validateUsingBarsValidateResponse(response: BarsValidationResponse): Form[BusinessVerificationRequest] =
      validate(response.accountNumberWithSortCodeIsValid, response.nonStandardAccountDetailsRequiredForBacs)

    def validateUsingBarsBusinessAssessResponse(
      response: BarsBusinessAssessResponse
    ): Form[BusinessVerificationRequest] =
      validateBusiness(
        response.accountNumberWithSortCodeIsValid,
        response.nonStandardAccountDetailsRequiredForBacs.getOrElse(No),
        Some(response.accountExists)
      )

    private def validate(
      accountNumberWithSortCodeIsValid: ReputationResponseEnum,
      nonStandardAccountDetailsRequiredForBacs: ReputationResponseEnum,
      accountExists: Option[ReputationResponseEnum] = None
    ): Form[BusinessVerificationRequest] =
      if (accountNumberWithSortCodeIsValid == No)
        form
          .fill(form.get)
          .withError("accountNumber", "error.accountNumber.modCheckFailed")
      else if (accountExists.isDefined && accountExists.get == No)
        form
          .fill(form.get)
          .withError("accountNumber", "error.accountNumber.doesNotExist")
      else if (nonStandardAccountDetailsRequiredForBacs == Yes && form.get.rollNumber.isEmpty)
        form
          .fill(form.get)
          .withError("rollNumber", "error.rollNumber.required")
      else form

    private def validateBusiness(
      accountNumberWithSortCodeIsValid: ReputationResponseEnum,
      nonStandardAccountDetailsRequiredForBacs: ReputationResponseEnum,
      accountExists: Option[ReputationResponseEnum] = None
    ): Form[BusinessVerificationRequest] =
      if (accountNumberWithSortCodeIsValid == No)
        form
          .fill(form.get)
          .withError("accountNumber", "error.accountNumber.modCheckFailed")
      else if (accountExists.isDefined && accountExists.get == No)
        form
          .fill(form.get)
          .withError("accountNumber", "error.accountNumber.doesNotExist")
      else if (nonStandardAccountDetailsRequiredForBacs == Yes && form.get.rollNumber.isEmpty)
        form
          .fill(form.get)
          .withError("rollNumber", "error.rollNumber.required")
      else form
  }

  val form: Form[BusinessVerificationRequest] =
    Form(
      mapping(
        "companyName"               -> companyNameMapping,
        "companyRegistrationNumber" -> optional(companyRegistrationNumberMapping),
        "sortCode"                  -> sortCodeMapping,
        "accountNumber"             -> accountNumberMapping,
        "rollNumber"                -> optional(rollNumberMapping)
      )(BusinessVerificationRequest.apply)(BusinessVerificationRequest.unapply)
    )

  def companyNameMapping = text.verifying(Constraints.nonEmpty(errorMessage = "error.companyName.required"))

  def companyRegistrationNumberMapping =
    text.verifying(
      Constraints
        .pattern(
          "^(OC|LP|SC|SO|SL|NI|R|NC|NL|oc|lp|sc|so|sl|ni|r|nc|nl)?[0-9]{6,8}$".r,
          name = "constraint.companyRegistrationNumber",
          error = "error.companyRegistrationNumber.invalid"
        )
    )
}
