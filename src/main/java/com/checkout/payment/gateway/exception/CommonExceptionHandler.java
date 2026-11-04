package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ErrorResponse;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  private static final Map<String, String> FIELD_MESSAGES = Map.of(
      "cardNumber", "Card number must be 14 to 19 digits",
      "expiryMonth", "Expiry month must be between 1 and 12",
      "expiryYear", "Expiry year must be a four-digit year",
      "currency", "Currency must be EUR, GBP, or USD",
      "amount", "Amount must be a positive integer",
      "cvv", "CVV must be 3 or 4 digits");

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.warn("Payment not found: {}", ex.getMessage());
    return new ResponseEntity<>(new ErrorResponse("Page not found"),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(InvalidPaymentException.class)
  public ResponseEntity<ErrorResponse> handleInvalidPayment(InvalidPaymentException ex) {
    String message = String.join(", ", ex.getErrors());
    return new ResponseEntity<>(
        new ErrorResponse(message, PaymentStatus.REJECTED.getName()),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(BankUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleBankUnavailable(BankUnavailableException ex) {
    return new ResponseEntity<>(new ErrorResponse(ex.getMessage()),
        HttpStatus.SERVICE_UNAVAILABLE);
  }

  @ExceptionHandler(RestClientException.class)
  public ResponseEntity<ErrorResponse> handleBankError(RestClientException ex) {
    LOG.warn("Acquiring bank request failed: {}", ex.getClass().getSimpleName());
    return new ResponseEntity<>(new ErrorResponse("Acquiring bank request failed"),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
    LOG.warn("Payment request could not be read");
    return new ResponseEntity<>(
        new ErrorResponse(fieldMessage(ex), PaymentStatus.REJECTED.getName()),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    LOG.warn("Invalid value for request parameter {}", ex.getName());
    String message = "id".equals(ex.getName())
        ? "Payment id must be a UUID"
        : "Request parameter is invalid";
    return new ResponseEntity<>(new ErrorResponse(message), HttpStatus.BAD_REQUEST);
  }

  private String fieldMessage(HttpMessageNotReadableException ex) {
    if (ex.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
      String field = mapping.getPath().get(mapping.getPath().size() - 1).getFieldName();
      if (field != null && FIELD_MESSAGES.containsKey(field)) {
        return FIELD_MESSAGES.get(field);
      }
    }
    return "Payment request could not be read";
  }
}
