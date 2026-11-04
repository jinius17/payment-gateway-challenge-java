# Decisions

The gateway validates a merchant payment, sends a valid one to the bank simulator, and stores the masked result so it can be retrieved later. Invalid payments never reach the bank. The in-memory repository from the skeleton is the only storage.

## API

`POST /payment` accepts `cardNumber`, `expiryMonth`, `expiryYear`, `currency`, `amount`, and `cvv`. Field names match the existing `GET /payment/{id}` response, which uses camelCase.

- `200` with status `Authorized` or `Declined` when the bank returns a decision. The body includes the id, status, last four card digits, expiry month, expiry year, currency, and amount. Both decisions use `200` because the gateway processed the request and the bank made a decision. The merchant reads the outcome from `status`.
- `400` with status `Rejected` and a message when the request is invalid. Nothing is stored. The response does not echo the payload. Every problem found in the body is reported together, in one message. A body that is not a complete JSON object is reported on its own as `Payment request could not be read`.
  - A field sent as the wrong JSON type names that type, for example `Expiry month must be a JSON integer`. An explicit `null` counts as the wrong type. A field that is missing is reported by its value rule, for example `Amount must be a positive integer`.
  - A value rule is not applied to a field that was sent as the wrong JSON type. Every other field is still checked.
  - A field that appears twice is rejected, for example `Amount must appear only once`, rather than keeping one of the values. A field that is not part of the payment is rejected with `Request body contains an unknown field`. Content after the JSON object is rejected with `Request body has trailing content`.
  - A JSON integer that does not fit the field is outside the supported range. That check uses the number itself, not the parser's error text.
- `415` when the `Content-Type` is not `application/json`. The body has a message and no payment status, because no payment was examined.
- `503` when the bank responds with `503`. Nothing is stored. This is not a rejection: the card details were valid, and the bank did not make a decision.
- `500` when the bank times out or returns any status other than `503`. Nothing is stored. The body has a message and no payment status.
- `GET /payment/{id}` is unchanged. An id that is not a UUID returns `400` with a message and no payment status. An unknown id returns `404` with `Payment not found`.

`POST /payment` reads the body as text and walks the JSON itself. That is what allows a repeated field to be reported next to the other errors, which a typed request object cannot do. The cost is that the OpenAPI document cannot infer the request schema, so the controller declares it with annotations, and a test checks the generated document.

The bank authorization code is not stored or returned. The merchant response defined for this exercise does not include it.

## Validation

- The card number is 14 to 19 digits and must be sent as text. A JSON number is rejected, so a leading zero cannot be dropped before validation.
- The CVV is 3 or 4 digits and must be sent as text, so a leading zero is preserved.
- The expiry month is 1 to 12. The expiry year is four digits. The month and year together must not be in the past. A card is treated as valid through the end of its expiry month, so the current month is still accepted.
- The amount is a positive integer in minor units and must be sent as a JSON integer. A fraction such as `10.5` is rejected rather than truncated.
- The currency is one of `EUR`, `GBP`, or `USD`. The exercise asks for at most three codes. `GBP` is the simulator example and `USD` is already used by the existing test.

## What is stored

A stored payment has a UUID, the bank decision, the last four digits, the expiry, the currency, and the amount. The full card number and the CVV are not stored, logged, or returned. A stored payment is logged with its id, status, and last four digits. An unknown id and a bank failure are warnings, without a stack trace and without the request body. The last four digits are text, so a leading zero is preserved.

Payments are kept in the skeleton `PaymentsRepository`, now backed by a `ConcurrentHashMap` so overlapping requests are safe. It lives for the process and is never trimmed. There is no database.

## Bank simulator

`BankClient` posts to `bank.simulator.payments-url`, which defaults to `http://localhost:8080/payments`. `BANK_SIMULATOR_PAYMENTS_URL` overrides that property. In Docker Compose it points at the simulator service. The simulator body uses its own names: `card_number`, `expiry_date` as `MM/yyyy`, `currency`, `amount`, and `cvv`.

A card ending in an odd digit is authorized. A card ending in 2, 4, 6, or 8 is declined. A card ending in 0 makes the simulator return `503`, which the gateway surfaces as unavailable. The simulator configuration in `imposters/` is unchanged.

## Known limitations

These are left out on purpose, to keep the exercise small. Each would be the next step for a real service.

- **A bank timeout can hide an authorization.** If the bank authorizes and the response is lost, the merchant gets `500`, nothing is stored, and a retry can charge the card again. A real gateway needs an idempotency key on `POST /payment`, so a retry returns the first result, and a way to reconcile with the bank.
- **No retries, circuit breaker, or limit on bank calls.** A slow bank holds a request thread for up to the 10 second timeout.
- **Storage is in memory.** A restart loses every payment. Rejected requests are not kept either. The bank authorization code is read and dropped, where a real gateway would keep it for refunds and reconciliation.
- **Operations.** There is no health endpoint, metrics, or request id in the logs, and the container runs as root. Logs show the outcome of each payment with the last four digits only.
- **No authentication or merchant identity**, and no API versioning. TLS and access logging are assumed to be handled in front of the service, so card data is never written out by this process.
- **No test runs against the real simulator.** The bank is mocked in the automated tests. The `curl` examples in the README are the manual check against `docker-compose up`.
- **No CI.** Tests run with `./gradlew test` on JDK 17.
