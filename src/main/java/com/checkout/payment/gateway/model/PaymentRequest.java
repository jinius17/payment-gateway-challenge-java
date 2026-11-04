package com.checkout.payment.gateway.model;

import io.swagger.v3.oas.annotations.media.Schema;

public class PaymentRequest {

  @Schema(description = "14 to 19 digits, sent as a JSON string", example = "2222405343248877")
  private String cardNumber;
  @Schema(description = "1 to 12, sent as a JSON integer", example = "4")
  private Integer expiryMonth;
  @Schema(description = "Four digits, sent as a JSON integer. The month and year must not be "
      + "in the past.", example = "2030")
  private Integer expiryYear;
  @Schema(description = "EUR, GBP, or USD", example = "GBP")
  private String currency;
  @Schema(description = "Positive amount in the minor currency unit, sent as a JSON integer",
      example = "100")
  private Integer amount;
  @Schema(description = "3 or 4 digits, sent as a JSON string", example = "123")
  private String cvv;

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public Integer getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(Integer expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public Integer getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(Integer expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Integer getAmount() {
    return amount;
  }

  public void setAmount(Integer amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }
}
