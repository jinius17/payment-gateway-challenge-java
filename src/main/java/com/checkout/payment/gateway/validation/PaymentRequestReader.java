package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.exception.InvalidPaymentException;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestReader {

  private static final List<Field> FIELDS = List.of(
      new Field("cardNumber", PaymentField.CARD_NUMBER, false,
          "Card number must be a JSON string", null),
      new Field("expiryMonth", PaymentField.EXPIRY_MONTH, true,
          "Expiry month must be a JSON integer", "Expiry month is outside the supported range"),
      new Field("expiryYear", PaymentField.EXPIRY_YEAR, true,
          "Expiry year must be a JSON integer", "Expiry year is outside the supported range"),
      new Field("currency", PaymentField.CURRENCY, false,
          "Currency must be a JSON string", null),
      new Field("amount", PaymentField.AMOUNT, true,
          "Amount must be a JSON integer", "Amount is outside the supported range"),
      new Field("cvv", PaymentField.CVV, false,
          "CVV must be a JSON string", null));

  private final ObjectMapper objectMapper;
  private final PaymentValidator paymentValidator;

  public PaymentRequestReader(ObjectMapper objectMapper, PaymentValidator paymentValidator) {
    this.objectMapper = objectMapper;
    this.paymentValidator = paymentValidator;
  }

  public PaymentRequest read(String json) {
    JsonNode body = parseObject(json);
    if (body == null || !body.isObject()) {
      throw new InvalidPaymentException(List.of("Payment request could not be read"));
    }

    PaymentRequest request = new PaymentRequest();
    Map<PaymentField, String> problems = new HashMap<>();
    for (Field field : FIELDS) {
      readField(body.get(field.name), field, request, problems);
    }
    List<String> errors = paymentValidator.validate(request, problems);
    if (!errors.isEmpty()) {
      throw new InvalidPaymentException(errors);
    }
    return request;
  }

  private ObjectReader valueReader() {
    // A field value is not the end of the body. Trailing content is checked once
    // the whole object has been read.
    return objectMapper.reader().without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  }

  private JsonNode parseObject(String json) {
    try (JsonParser parser = objectMapper.createParser(json)) {
      // The mapper rejects duplicate names. This walk reports them itself, so the
      // response does not depend on the wording of Jackson's exception.
      parser.disable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        return null;
      }
      ObjectNode object = objectMapper.createObjectNode();
      Set<String> names = new HashSet<>();
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        if (!parser.hasToken(JsonToken.FIELD_NAME)) {
          return null;
        }
        String name = parser.currentName();
        if (!names.add(name)) {
          throw new InvalidPaymentException(List.of("Request body contains a duplicate field"));
        }
        parser.nextToken();
        object.set(name, valueReader().readTree(parser));
      }
      if (hasTrailingToken(parser)) {
        throw new InvalidPaymentException(List.of("Request body has trailing content"));
      }
      return object;
    } catch (InvalidPaymentException exception) {
      throw exception;
    } catch (IOException exception) {
      throw new InvalidPaymentException(List.of("Payment request could not be read"));
    }
  }

  private boolean hasTrailingToken(JsonParser parser) {
    try {
      return parser.nextToken() != null;
    } catch (IOException exception) {
      return true;
    }
  }

  private void readField(JsonNode value, Field field, PaymentRequest request,
      Map<PaymentField, String> problems) {
    if (value == null || value.isNull()) {
      return;
    }
    if (field.integer) {
      readInteger(value, field, request, problems);
    } else if (value.isTextual()) {
      setText(field.paymentField, value.textValue(), request);
    } else {
      problems.put(field.paymentField, field.typeMessage);
    }
  }

  private void readInteger(JsonNode value, Field field, PaymentRequest request,
      Map<PaymentField, String> problems) {
    if (!value.isIntegralNumber()) {
      problems.put(field.paymentField, field.typeMessage);
    } else if (!value.canConvertToInt()) {
      problems.put(field.paymentField, field.rangeMessage);
    } else {
      setInteger(field.paymentField, value.intValue(), request);
    }
  }

  private void setText(PaymentField field, String value, PaymentRequest request) {
    switch (field) {
      case CARD_NUMBER -> request.setCardNumber(value);
      case CURRENCY -> request.setCurrency(value);
      case CVV -> request.setCvv(value);
      default -> throw new IllegalStateException("Not a text field");
    }
  }

  private void setInteger(PaymentField field, int value, PaymentRequest request) {
    switch (field) {
      case EXPIRY_MONTH -> request.setExpiryMonth(value);
      case EXPIRY_YEAR -> request.setExpiryYear(value);
      case AMOUNT -> request.setAmount(value);
      default -> throw new IllegalStateException("Not an integer field");
    }
  }

  private record Field(String name, PaymentField paymentField, boolean integer,
                        String typeMessage, String rangeMessage) {
  }
}
