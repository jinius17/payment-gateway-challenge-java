package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestClientException;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

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
    LOG.warn("Acquiring bank request failed: {}", ex.getMessage());
    return new ResponseEntity<>(new ErrorResponse("Acquiring bank request failed"),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
