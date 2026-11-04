package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.model.PaymentRequest;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PaymentValidator {

  static final Set<String> SUPPORTED_CURRENCIES = Set.of("EUR", "GBP", "USD");

  private final Clock clock;

  public PaymentValidator(Clock clock) {
    this.clock = clock;
  }

  public List<String> validate(PaymentRequest request) {
    if (request == null) {
      return List.of("Payment request is required");
    }

    List<String> errors = new ArrayList<>();
    validateCardNumber(request.getCardNumber(), errors);
    validateExpiry(request.getExpiryMonth(), request.getExpiryYear(), errors);
    validateCurrency(request.getCurrency(), errors);
    validateAmount(request.getAmount(), errors);
    validateCvv(request.getCvv(), errors);
    return List.copyOf(errors);
  }

  private void validateCardNumber(String cardNumber, List<String> errors) {
    if (cardNumber == null || !cardNumber.matches("\\d{14,19}")) {
      errors.add("Card number must be 14 to 19 digits");
    }
  }

  private void validateExpiry(Integer expiryMonth, Integer expiryYear, List<String> errors) {
    boolean monthValid = expiryMonth != null && expiryMonth >= 1 && expiryMonth <= 12;
    if (!monthValid) {
      errors.add("Expiry month must be between 1 and 12");
    }
    boolean yearValid = expiryYear != null && expiryYear >= 1000 && expiryYear <= 9999;
    if (expiryYear == null) {
      errors.add("Expiry year is required");
    } else if (!yearValid) {
      errors.add("Expiry year must be a four-digit year");
    }
    if (monthValid && yearValid) {
      YearMonth expiry = YearMonth.of(expiryYear, expiryMonth);
      if (expiry.isBefore(YearMonth.now(clock))) {
        errors.add("Expiry month and year must not be in the past");
      }
    }
  }

  private void validateCurrency(String currency, List<String> errors) {
    if (currency == null || !SUPPORTED_CURRENCIES.contains(currency)) {
      errors.add("Currency must be EUR, GBP, or USD");
    }
  }

  private void validateAmount(Integer amount, List<String> errors) {
    if (amount == null || amount < 1) {
      errors.add("Amount must be a positive integer");
    }
  }

  private void validateCvv(String cvv, List<String> errors) {
    if (cvv == null || !cvv.matches("\\d{3,4}")) {
      errors.add("CVV must be 3 or 4 digits");
    }
  }
}
