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

package bankaccountverification

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

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Json, OWrites}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONLong, BSONObjectID}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SessionDataRepository @Inject() (component: ReactiveMongoComponent)
    extends ReactiveRepository[MongoSessionData, BSONObjectID](
      "bank-account-verification-session-store",
      component.mongoConnector.db,
      MongoSessionData.format
    ) {

  val expireAfterSeconds: Long = 0

  private lazy val ExpiryDateIndex       = "expiryDateIndex"
  private lazy val OptExpireAfterSeconds = "expireAfterSeconds"

  def insertOne(entity: MongoSessionData)(implicit ec: ExecutionContext): Future[Boolean] =
    insert(entity).map(wc => wc.ok)

  def findAndUpdateById(id: BSONObjectID, data: SessionData)(implicit
    formats: OWrites[MongoSessionData],
    ec: ExecutionContext
  ): Future[Boolean] =
    findAndUpdate(_id(id), Json.toJsObject(MongoSessionData.createExpiring(id, Some(data)))).map(r =>
      r.lastError.isDefined
    )

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    import reactivemongo.bson.DefaultBSONHandlers._

    val indexes = collection.indexesManager.list()
    indexes.flatMap { idxs =>
      val expiry = idxs.find(index =>
        index.eventualName == ExpiryDateIndex
          && index.options.getAs[BSONLong](OptExpireAfterSeconds).fold(false)(_.as[Long] != expireAfterSeconds)
      )

      Future.sequence(Seq(ensureExpiryDateIndex(expiry)))
    }
  }

  private def ensureExpiryDateIndex(existingIndex: Option[Index])(implicit ec: ExecutionContext) = {
    logger.info(s"Creating time to live for entries in ${collection.name} to $expireAfterSeconds seconds")

    existingIndex
      .fold(Future.successful(0))(idx => collection.indexesManager.drop(idx.eventualName))
      .flatMap { _ =>
        collection.indexesManager.ensure(
          Index(
            key = Seq("expiryDate" -> IndexType.Ascending),
            name = Some(ExpiryDateIndex),
            options = BSONDocument(OptExpireAfterSeconds -> expireAfterSeconds)
          )
        )
      }
  }
}
