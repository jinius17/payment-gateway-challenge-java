package com.checkout.payment.gateway.exception;

public class BankUnavailableException extends RuntimeException {

  public BankUnavailableException() {
    super("Acquiring bank is unavailable");
  }
}
