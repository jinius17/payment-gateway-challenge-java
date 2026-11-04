# Decisions

The gateway validates a merchant payment, sends a valid one to the bank simulator, and stores the masked result so it can be retrieved later. Invalid payments never reach the bank. The in-memory repository from the skeleton is the only storage.

## API

`POST /payment` accepts `cardNumber`, `expiryMonth`, `expiryYear`, `currency`, `amount`, and `cvv`. Field names match the existing `GET /payment/{id}` response, which uses camelCase.

- `200` with status `Authorized` or `Declined` when the bank returns a decision. The body includes the id, status, last four card digits, expiry month, expiry year, currency, and amount. Both decisions use `200` because the gateway processed the request and the bank made a decision. The merchant reads the outcome from `status`.
- `400` with status `Rejected` and a message when the request is invalid, including a body that cannot be read, a duplicate field, or trailing content. Nothing is stored. The response does not echo the payload. A duplicate field is rejected rather than keeping one of the values. A field sent as the wrong JSON type names that type, for example `Expiry month must be a JSON integer`. Value rules use the same sentence form, for example `Amount must be a positive integer`. A value rule is not applied to a field that was sent as the wrong JSON type, and every other field is still checked. A JSON integer that does not fit the field is outside the supported range. That check uses the number itself, not the parser's error text.
- `503` when the bank responds with `503`. Nothing is stored. This is not a rejection: the card details were valid, and the bank did not make a decision.
- `500` when the bank times out or returns any status other than `503`. Nothing is stored. The body has a message and no payment status.
- `GET /payment/{id}` is unchanged. An id that is not a UUID returns `400` with a message and no payment status. An unknown id returns `404`.

The bank authorization code is not stored or returned. The merchant response defined for this exercise does not include it.

## Validation

- The card number is 14 to 19 digits and must be sent as text. A JSON number is rejected, so a leading zero cannot be dropped before validation.
- The CVV is 3 or 4 digits and must be sent as text, so a leading zero is preserved.
- The expiry month is 1 to 12. The expiry year is four digits. The month and year together must not be in the past. A card is treated as valid through the end of its expiry month, so the current month is still accepted.
- The amount is a positive integer in minor units and must be sent as a JSON integer. A fraction such as `10.5` is rejected rather than truncated.
- The currency is one of `EUR`, `GBP`, or `USD`. The exercise asks for at most three codes. `GBP` is the simulator example and `USD` is already used by the existing test.

## What is stored

A stored payment has a UUID, the bank decision, the last four digits, the expiry, the currency, and the amount. The full card number and the CVV are not stored, logged, or returned. A stored payment is logged with its id, status, and last four digits. An unknown id and a bank failure are warnings, without a stack trace and without the request body. The last four digits are text, so a leading zero is preserved.

Payments are kept in the skeleton `PaymentsRepository`, a hash map that lives for the process. There is no database.

## Bank simulator

`BankClient` posts to `bank.simulator.payments-url`, which defaults to `http://localhost:8080/payments`. `BANK_SIMULATOR_PAYMENTS_URL` overrides that property. In Docker Compose it points at the simulator service. The simulator body uses its own names: `card_number`, `expiry_date` as `MM/yyyy`, `currency`, `amount`, and `cvv`.

A card ending in an odd digit is authorized. A card ending in 2, 4, 6, or 8 is declined. A card ending in 0 makes the simulator return `503`, which the gateway surfaces as unavailable. The simulator configuration in `imposters/` is unchanged.
