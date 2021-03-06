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

package bankaccountverification

import java.time.{Instant, ZoneOffset, ZonedDateTime}

import bankaccountverification.connector.ReputationResponseEnum
import bankaccountverification.web.AccountTypeRequestEnum
import play.api.libs.functional.syntax._
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

case class Journey(id: BSONObjectID, internalAuthId: Option[String], expiryDate: ZonedDateTime, serviceIdentifier: String, continueUrl: String,
                   data: Session, messages: Option[JsObject] = None, customisationsUrl: Option[String] = None)

object Journey {
  def expiryDate = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(60)

  def createSession(address: Option[Address], prepopulatedData: Option[PrepopulatedData]): Session = {
    val session = Session(address = address)

    prepopulatedData match {
      case None => session
      case Some(p) => {
        p.accountType match {
          case AccountTypeRequestEnum.Personal =>
            session.copy(
              accountType = Some(p.accountType),
              personal = Some(PersonalSession(accountName = p.name, sortCode = p.sortCode,
                accountNumber = p.accountNumber, rollNumber = p.rollNumber)))
          case AccountTypeRequestEnum.Business =>
            session.copy(
              accountType = Some(p.accountType),
              business = Some(BusinessSession(companyName = p.name, sortCode = p.sortCode,
                accountNumber = p.accountNumber, rollNumber = p.rollNumber)))
        }
      }
    }
  }

  def createExpiring(id: BSONObjectID, internalAuthId: Option[String], serviceIdentifier: String, continueUrl: String,
                     messages: Option[JsObject] = None, customisationsUrl: Option[String] = None,
                     address: Option[Address] = None, prepopulatedData: Option[PrepopulatedData] = None): Journey =
    Journey(id, internalAuthId, expiryDate, serviceIdentifier, continueUrl, createSession(address, prepopulatedData),
      messages, customisationsUrl)

  def updatePersonalAccountDetailsExpiring(data: PersonalAccountDetails) =
    PersonalAccountDetailsUpdate(expiryDate, data)

  def updateBusinessAccountDetailsExpiring(data: BusinessAccountDetails) =
    BusinessAccountDetailsUpdate(expiryDate, data)

  def updateAccountTypeExpiring(accountType: AccountTypeRequestEnum) = AccountTypeUpdate(expiryDate, accountType)

  implicit val objectIdFormats: Format[BSONObjectID] = ReactiveMongoFormats.objectIdFormats
  implicit val personalSessionDataReads: Reads[PersonalSession] = Json.reads[PersonalSession]
  implicit val personalSessionDataWrites: Writes[PersonalSession] = Json.writes[PersonalSession]
  implicit val businessSessionDataReads: Reads[BusinessSession] = Json.reads[BusinessSession]
  implicit val businessSessionDataWrites: Writes[BusinessSession] = Json.writes[BusinessSession]

  implicit val sessionAddressReads: Reads[Address] = Json.reads[Address]
  implicit val sessionAddressWrites: Writes[Address] = Json.writes[Address]

  implicit val sessionReads: Reads[Session] = Json.reads[Session]
  implicit val sessionWrites: Writes[Session] = Json.writes[Session]

  implicit val localDateTimeRead: Reads[ZonedDateTime] =
    (__ \ "$date").read[Long].map { dateTime =>
      ZonedDateTime.ofInstant(Instant.ofEpochMilli(dateTime), ZoneOffset.UTC)
    }

  implicit val localDateTimeWrite: Writes[ZonedDateTime] = new Writes[ZonedDateTime] {
    def writes(dateTime: ZonedDateTime): JsValue =
      Json.obj(
        "$date" -> dateTime.toInstant.toEpochMilli
      )
  }

  implicit val datetimeFormat: Format[ZonedDateTime] = Format(localDateTimeRead, localDateTimeWrite)

  implicit def defaultReads: Reads[Journey] =
    (__ \ "_id")
      .read[BSONObjectID]
      .and((__ \ "internalAuthId").readNullable[String])
      .and((__ \ "expiryDate").read[ZonedDateTime])
      .and((__ \ "serviceIdentifier").read[String])
      .and((__ \ "continueUrl").read[String])
      .and((__ \ "data").read[Session])
      .and((__ \ "messages").readNullable[JsObject])
      .and((__ \ "customisationsUrl").readNullable[String])(
        (
          id: BSONObjectID,
          internalAuthId: Option[String],
          expiryDate: ZonedDateTime,
          serviceIdentifier: String,
          continueUrl: String,
          data: Session,
          messages: Option[JsObject],
          customisationsUrl: Option[String]
        ) => Journey.apply(id, internalAuthId, expiryDate, serviceIdentifier, continueUrl, data, messages, customisationsUrl)
      )

  implicit def defaultWrites: OWrites[Journey] =
    (__ \ "_id")
      .write[BSONObjectID]
      .and((__ \ "internalAuthId").write[Option[String]])
      .and((__ \ "expiryDate").write[ZonedDateTime])
      .and((__ \ "serviceIdentifier").write[String])
      .and((__ \ "continueUrl").write[String])
      .and((__ \ "data").write[Session])
      .and((__ \ "messages").writeNullable[JsObject])
      .and((__ \ "customisationsUrl").writeNullable[String]) {
        unlift(Journey.unapply)
      }

  implicit def personalAccountDetailsWrites: OWrites[PersonalAccountDetails] =
    (__ \ "data.personal.accountName")
      .writeNullable[String]
      .and((__ \ "data.personal.sortCode").writeNullable[String])
      .and((__ \ "data.personal.accountNumber").writeNullable[String])
      .and((__ \ "data.personal.rollNumber").writeOptionWithNull[String])
      .and((__ \ "data.personal.accountNumberWithSortCodeIsValid").writeNullable[ReputationResponseEnum])
      .and((__ \ "data.personal.accountExists").writeNullable[ReputationResponseEnum])
      .and((__ \ "data.personal.nameMatches").writeNullable[ReputationResponseEnum])
      .and((__ \ "data.personal.addressMatches").writeNullable[ReputationResponseEnum])
      .and((__ \ "data.personal.nonConsented").writeNullable[ReputationResponseEnum])
      .and((__ \ "data.personal.subjectHasDeceased").writeNullable[ReputationResponseEnum])
      .and((__ \ "data.personal.nonStandardAccountDetailsRequiredForBacs").writeNullable[ReputationResponseEnum])
      .and((__ \ "data.personal.sortCodeBankName").writeOptionWithNull[String])(
        unlift(PersonalAccountDetails.unapply)
      )

  implicit def businessAccountDetailsWrites: OWrites[BusinessAccountDetails] =
    (__ \ "data.business.companyName")
      .writeNullable[String]
      .and((__ \ "data.business.sortCode").writeNullable[String])
      .and((__ \ "data.business.accountNumber").writeNullable[String])
      .and((__ \ "data.business.rollNumber").writeOptionWithNull[String])
      .and((__ \ "data.business.accountNumberWithSortCodeIsValid").writeNullable[ReputationResponseEnum])
      .and((__ \ "data.business.nonStandardAccountDetailsRequiredForBacs").writeNullable[ReputationResponseEnum])
      .and((__ \ "data.business.accountExists").writeNullable[ReputationResponseEnum])
      .and((__ \ "data.business.companyNameMatches").writeNullable[ReputationResponseEnum])
      .and((__ \ "data.business.companyPostCodeMatches").writeNullable[ReputationResponseEnum])
      .and((__ \ "data.business.companyRegistrationNumberMatches").writeNullable[ReputationResponseEnum])
      .and((__ \ "data.business.sortCodeBankName").writeOptionWithNull[String])(
        unlift(BusinessAccountDetails.unapply)
      )

  implicit def personalUpdateWrites: OWrites[PersonalAccountDetailsUpdate] = {
    (__ \ "$set" \ "expiryDate")
      .write[ZonedDateTime]
      .and((__ \ "$set").write[PersonalAccountDetails])(
        unlift(PersonalAccountDetailsUpdate.unapply))
  }

  implicit def businessUpdateWrites: OWrites[BusinessAccountDetailsUpdate] =
    (__ \ "$set" \ "expiryDate")
      .write[ZonedDateTime]
      .and((__ \ "$set").write[BusinessAccountDetails])(
        unlift(BusinessAccountDetailsUpdate.unapply)
      )

  implicit def accountTypeUpdateWrites: OWrites[AccountTypeUpdate] =
    (__ \ "$set" \ "expiryDate")
      .write[ZonedDateTime]
      .and((__ \ "$set" \ "data.accountType").write[AccountTypeRequestEnum])(
        unlift(AccountTypeUpdate.unapply)
      )

  val format: Format[Journey] = Format(defaultReads, defaultWrites)
}
