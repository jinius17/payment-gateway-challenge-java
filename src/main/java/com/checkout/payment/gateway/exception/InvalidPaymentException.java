package com.checkout.payment.gateway.exception;

import java.util.List;

public class InvalidPaymentException extends RuntimeException {

  private final List<String> errors;

  public InvalidPaymentException(List<String> errors) {
    super(String.join(", ", errors));
    this.errors = List.copyOf(errors);
  }

  public List<String> getErrors() {
    return errors;
  }
}
