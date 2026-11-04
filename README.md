# Payment gateway

A merchant submits a card payment. The gateway validates it, asks the acquiring-bank simulator for a decision, and stores the masked result so it can be retrieved.

Design choices are in [decisions.md](decisions.md).

## Run

Requirements: JDK 17 and Docker.

Start the simulator and the gateway:

```bash
docker-compose up --build
```

The gateway listens on port 8090. The simulator listens on port 8080. Inside Compose the gateway calls the simulator at `http://bank_simulator:8080/payments`, set with `BANK_SIMULATOR_PAYMENTS_URL`.

To run the gateway on the host instead, start only the simulator and then the app. The default bank URL is `http://localhost:8080/payments`.

```bash
docker-compose up bank_simulator
./gradlew bootRun
```

## Examples

The simulator authorizes a card ending in an odd digit, declines a card ending in 2, 4, 6, or 8, and returns 503 for a card ending in 0.

Authorized:

```bash
curl -i -X POST http://localhost:8090/payment \
  -H 'Content-Type: application/json' \
  -d '{"cardNumber":"2222405343248877","expiryMonth":4,"expiryYear":2030,"currency":"GBP","amount":100,"cvv":"123"}'
```

Retrieve the payment using the `id` from that response:

```bash
curl -i http://localhost:8090/payment/<id>
```

Declined:

```bash
curl -i -X POST http://localhost:8090/payment \
  -H 'Content-Type: application/json' \
  -d '{"cardNumber":"2222405343248112","expiryMonth":4,"expiryYear":2030,"currency":"GBP","amount":100,"cvv":"123"}'
```

Rejected. The amount is invalid, so the bank is not called:

```bash
curl -i -X POST http://localhost:8090/payment \
  -H 'Content-Type: application/json' \
  -d '{"cardNumber":"2222405343248877","expiryMonth":4,"expiryYear":2030,"currency":"GBP","amount":0,"cvv":"123"}'
```

Unavailable. Nothing is stored:

```bash
curl -i -X POST http://localhost:8090/payment \
  -H 'Content-Type: application/json' \
  -d '{"cardNumber":"2222405343248870","expiryMonth":4,"expiryYear":2030,"currency":"GBP","amount":100,"cvv":"123"}'
```

## Tests

```bash
./gradlew test
```

The tests need JDK 17. They do not run on JDK 21 or later, because Mockito in the pinned Spring Boot 3.1.5 cannot modify classes there.

## API documentation

Swagger UI is at http://localhost:8090/swagger-ui/index.html. The OpenAPI document is at http://localhost:8090/v3/api-docs. It describes the request, the response, and each error status.

## Project layout

- `src/main` is the gateway: controller, service, validation, bank client, and an in-memory repository.
- `src/test` has unit tests for each layer and MockMvc tests for the API.
- `imposters/` is the bank simulator configuration. It is unchanged from the template.
- `docker-compose.yml` runs the simulator and the gateway.
- `decisions.md` records the design decisions, the rules, and the known limitations.