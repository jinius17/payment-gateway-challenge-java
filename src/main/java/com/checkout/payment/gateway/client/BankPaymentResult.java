package com.checkout.payment.gateway.client;

public final class BankPaymentResult {

  private final boolean authorized;
  private final String authorizationCode;
  private final boolean unavailable;

  private BankPaymentResult(boolean authorized, String authorizationCode, boolean unavailable) {
    this.authorized = authorized;
    this.authorizationCode = authorizationCode;
    this.unavailable = unavailable;
  }

  public static BankPaymentResult from(boolean authorized, String authorizationCode) {
    return new BankPaymentResult(authorized, authorizationCode, false);
  }

  public static BankPaymentResult unavailable() {
    return new BankPaymentResult(false, null, true);
  }

  public boolean isAuthorized() {
    return authorized;
  }

  public String getAuthorizationCode() {
    return authorizationCode;
  }

  public boolean isUnavailable() {
    return unavailable;
  }
}
