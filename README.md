# bank-account-verification-frontend

`bank-account-verification-frontend` (`BAVFE`) provides a common frontend implementation for client services that need to capture, validate and verify bank account information.
It provides mechanisms to customise messaging, eg page titles, element labels, etc, to ensure that it _blends_ with the calling service.

An example of a client service can be seen in [bank-account-verification-example-frontend](https://github.com/hmrc/bank-account-verification-example-frontend) (`BAVEFE`).

## Flow
There are 3 parts to the usage of `BAVFE`:
1. **Initiate a journey** - Initialises a new journey with a unique id (provided by `BAVFE`) and customisation parameters. 
1. **Perform the journey** - Hands control to `BAVFE` which progresses the journey to capture, validate and verify bank account details.
1. **Complete the journey** - The client service calls `BAVFE` to collect the entered data and verification results.

In steps 2 & 3, if an invalid `journeyId` is provided or is missing, a `BadRequest` or `NotFound` is returned respectively.

### Initiate a journey
To initiate a journey a `POST` call must be made to `/api/init` with a payload that has the following data model:
```scala
case class InitRequest(
    serviceIdentifier: String, 
    continueUrl: String, 
    address: Option[InitRequestAddress] = None,
    messages: Option[InitRequestMessages] = None, 
    customisationsUrl: Option[String] = None
)
```

As can be seen, the only mandatory parameters are `serviceIdentifier` - the name of the calling service, and `continueUrl` - the callback url to call the `BAVFE` journey segment is completed. The `customisationsUrl` is used by `BAVFE` to retrieve `HtmlPartials` for customisation of `header`, `beforeContent` and `footer` sections of the pages. See [CustomisationsController](https://github.com/hmrc/bank-account-verification-example-frontend/blob/master/app/uk/gov/hmrc/bankaccountverificationexamplefrontend/bavf/CustomisationsController.scala) for an example implementation.

The optional `address` parameter has the following model:
```scala
case class InitRequestAddress(
  lines: List[String], 
  town: Option[String], 
  postcode: Option[String]
)
```

The optional `messages` parameter has the following model:
```scala
case class InitRequestMessages(
    en: JsObject, 
    cy: Option[JsObject]
)
```

All messages contained in [message.en](https://github.com/hmrc/bank-account-verification-frontend/blob/master/conf/messages.en) can be customised. If the client service supports Welsh translations, then Welsh customisations should be provided for every English equivalent.

### Perform the journey
Once the initiating call has been made and a `journeyId` retrieved, the journey can be started with a call to `/bank-account-verification/start/:journeyId` where `:journeyId` is that returned by the `initiate` call.
Control at this stage passes to `BAVFE` until the journey is complete. At the end of the journey, `BAVFE` calls back to the `continueUrl` (provided in the initiate call) to indicate to the client service that the journey is complete and results can be retrieved.

### Complete the journey
Once the `continueUrl` has been called by `BAVFE`, a call can be made to `/api/complete/:journeyId` to retrieve the results of the journey. The following data model describes the payload that is returned:

```scala
case class CompleteResponse(
    accountType: AccountTypeRequestEnum, 
    personal: Option[PersonalCompleteResponse],
    business: Option[BusinessCompleteResponse]
)
``` 

`accountType` can be either `personal` or `business`. This corresponds to the `personal` being defined or the `business` being defined in `CompleteResponse` above.

`personal` has the following data model:
```scala
case class PersonalCompleteResponse(
    address: Option[CompleteResponseAddress],
    accountName: String,
    sortCode: String,
    accountNumber: String,
    accountNumberWithSortCodeIsValid: ReputationResponseEnum,
    rollNumber: Option[String] = None,
    accountExists: Option[ReputationResponseEnum] = None,
    nameMatches: Option[ReputationResponseEnum] = None,
    addressMatches: Option[ReputationResponseEnum] = None,
    nonConsented: Option[ReputationResponseEnum] = None,
    subjectHasDeceased: Option[ReputationResponseEnum] = None,
    nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None,
    sortCodeBankName: Option[String] = None
)
```
`business` has the following data model:
```scala
case class BusinessCompleteResponse(
    address: Option[CompleteResponseAddress],
    companyName: String,
    sortCode: String,
    accountNumber: String,
    rollNumber: Option[String] = None,
    accountNumberWithSortCodeIsValid: ReputationResponseEnum,
    accountExists: Option[ReputationResponseEnum] = None,
    companyNameMatches: Option[ReputationResponseEnum],
    companyPostCodeMatches: Option[ReputationResponseEnum],
    companyRegistrationNumberMatches: Option[ReputationResponseEnum],
    nonStandardAccountDetailsRequiredForBacs: Option[ReputationResponseEnum] = None,
    sortCodeBankName: Option[String] = None
  )
```
### Writing acceptance tests for a service that uses `BAVFE` 

We suggest that you mock out `BAVFE` in any acceptance tests that you write.  This will speed up your tests and allow us to make changes to the GUI without affecting your tests.
 
`bank-account-verification-acceptance-example` is our recommended solution for creating acceptance tests for a service that integrates with `bank-account-verification-frontend`.  

Check it out on [Github](https://github.com/hmrc/bank-account-verification-acceptance-example).

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
