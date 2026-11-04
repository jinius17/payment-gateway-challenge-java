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

# Instructions for candidates

This is the Java version of the Payment Gateway challenge. If you haven't already read this [README.md](https://github.com/cko-recruitment/) on the details of this exercise, please do so now.

## Requirements
- JDK 17
- Docker

## Template structure

src/ - A skeleton SpringBoot Application

test/ - Some simple JUnit tests

imposters/ - contains the bank simulator configuration. Don't change this

.editorconfig - don't change this. It ensures a consistent set of rules for submissions when reformatting code

docker-compose.yml - configures the bank simulator


## API Documentation
For documentation openAPI is included, and it can be found under the following url: **http://localhost:8090/swagger-ui/index.html**

**Feel free to change the structure of the solution, use a different library etc.**