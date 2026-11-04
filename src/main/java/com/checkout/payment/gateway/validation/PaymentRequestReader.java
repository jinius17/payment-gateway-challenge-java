package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.exception.InvalidPaymentException;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestReader {

  static final String UNREADABLE = "Payment request could not be read";
  static final String UNKNOWN_FIELD = "Request body contains an unknown field";
  static final String TRAILING_CONTENT = "Request body has trailing content";

  private static final List<Field> FIELDS = List.of(
      new Field("cardNumber", PaymentField.CARD_NUMBER, "Card number", false),
      new Field("expiryMonth", PaymentField.EXPIRY_MONTH, "Expiry month", true),
      new Field("expiryYear", PaymentField.EXPIRY_YEAR, "Expiry year", true),
      new Field("currency", PaymentField.CURRENCY, "Currency", false),
      new Field("amount", PaymentField.AMOUNT, "Amount", true),
      new Field("cvv", PaymentField.CVV, "CVV", false));

  private static final Map<String, Field> FIELDS_BY_NAME = byName();

  private final ObjectMapper objectMapper;
  private final PaymentValidator paymentValidator;

  public PaymentRequestReader(ObjectMapper objectMapper, PaymentValidator paymentValidator) {
    this.objectMapper = objectMapper;
    this.paymentValidator = paymentValidator;
  }

  /**
   * Reads a payment from a JSON body. Every problem found in the body is reported together:
   * a field of the wrong JSON type, a repeated field, an unknown field, a value that breaks a
   * rule, and trailing content. A body that is not a JSON object is reported on its own.
   */
  public PaymentRequest read(String json) {
    Map<PaymentField, String> problems = new EnumMap<>(PaymentField.class);
    Set<String> bodyErrors = new LinkedHashSet<>();
    Map<String, JsonNode> values = parseObject(json, problems, bodyErrors);
    if (values == null) {
      throw new InvalidPaymentException(List.of(UNREADABLE));
    }

    PaymentRequest request = new PaymentRequest();
    for (Field field : FIELDS) {
      if (!problems.containsKey(field.paymentField())) {
        readField(values.get(field.jsonName()), field, request, problems);
      }
    }

    List<String> errors = new ArrayList<>(paymentValidator.validate(request, problems));
    errors.addAll(bodyErrors);
    if (!errors.isEmpty()) {
      throw new InvalidPaymentException(errors);
    }
    return request;
  }

  /**
   * Walks the object by hand so a repeated name can be reported next to the other errors.
   * Returns null when the body is not a complete JSON object.
   */
  private Map<String, JsonNode> parseObject(String json, Map<PaymentField, String> problems,
      Set<String> bodyErrors) {
    try (JsonParser parser = objectMapper.createParser(json)) {
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        return null;
      }
      Map<String, JsonNode> values = new HashMap<>();
      while (parser.nextToken() == JsonToken.FIELD_NAME) {
        String name = parser.currentName();
        parser.nextToken();
        Field field = FIELDS_BY_NAME.get(name);
        if (field == null) {
          bodyErrors.add(UNKNOWN_FIELD);
          parser.skipChildren();
        } else if (values.containsKey(name)) {
          problems.put(field.paymentField(), field.duplicateMessage());
          parser.skipChildren();
        } else {
          values.put(name, objectMapper.readTree(parser));
        }
      }
      if (parser.currentToken() != JsonToken.END_OBJECT) {
        return null;
      }
      if (hasTrailingToken(parser)) {
        bodyErrors.add(TRAILING_CONTENT);
      }
      return values;
    } catch (IOException exception) {
      return null;
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
    if (value == null) {
      return;
    }
    if (field.integer()) {
      readInteger(value, field, request, problems);
    } else if (value.isTextual()) {
      setText(field.paymentField(), value.textValue(), request);
    } else {
      problems.put(field.paymentField(), field.typeMessage());
    }
  }

  private void readInteger(JsonNode value, Field field, PaymentRequest request,
      Map<PaymentField, String> problems) {
    if (!value.isIntegralNumber()) {
      problems.put(field.paymentField(), field.typeMessage());
    } else if (!value.canConvertToInt()) {
      problems.put(field.paymentField(), field.rangeMessage());
    } else {
      setInteger(field.paymentField(), value.intValue(), request);
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

  private static Map<String, Field> byName() {
    Map<String, Field> byName = new HashMap<>();
    FIELDS.forEach(field -> byName.put(field.jsonName(), field));
    return Map.copyOf(byName);
  }

  private record Field(String jsonName, PaymentField paymentField, String label,
                        boolean integer) {

    String typeMessage() {
      return label + (integer ? " must be a JSON integer" : " must be a JSON string");
    }

    String rangeMessage() {
      return label + " is outside the supported range";
    }

    String duplicateMessage() {
      return label + " must appear only once";
    }
  }
}
