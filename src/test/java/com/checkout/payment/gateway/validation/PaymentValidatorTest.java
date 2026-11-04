package com.checkout.payment.gateway.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.model.PaymentRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentValidatorTest {

  private PaymentValidator paymentValidator;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);
    paymentValidator = new PaymentValidator(clock);
  }

  @Test
  void acceptsAPaymentThatMeetsEveryRule() {
    assertThat(paymentValidator.validate(validRequest())).isEmpty();
  }

  @Test
  void rejectsACardNumberThatIsNot14To19Digits() {
    PaymentRequest request = validRequest();
    request.setCardNumber("1234");

    assertThat(paymentValidator.validate(request))
        .contains("Card number must be 14 to 19 digits");
  }

  @Test
  void rejectsACardNumberThatContainsLetters() {
    PaymentRequest request = validRequest();
    request.setCardNumber("222240534324887a");

    assertThat(paymentValidator.validate(request))
        .contains("Card number must be 14 to 19 digits");
  }

  @Test
  void rejectsAnExpiryMonthOutside1To12() {
    PaymentRequest request = validRequest();
    request.setExpiryMonth(13);

    assertThat(paymentValidator.validate(request))
        .contains("Expiry month must be between 1 and 12");
  }

  @Test
  void rejectsAnExpiryYearThatIsNotFourDigits() {
    PaymentRequest request = validRequest();
    request.setExpiryYear(2000000000);

    assertThat(paymentValidator.validate(request))
        .contains("Expiry year must be a four-digit year");
  }

  @Test
  void rejectsAnExpiryDateInThePast() {
    PaymentRequest request = validRequest();
    request.setExpiryMonth(9);
    request.setExpiryYear(2026);

    assertThat(paymentValidator.validate(request))
        .contains("Expiry month and year must not be in the past");
  }

  @Test
  void acceptsTheCurrentExpiryMonth() {
    PaymentRequest request = validRequest();
    request.setExpiryMonth(10);
    request.setExpiryYear(2026);

    assertThat(paymentValidator.validate(request)).isEmpty();
  }

  @Test
  void rejectsACurrencyOutsideTheSupportedSet() {
    PaymentRequest request = validRequest();
    request.setCurrency("JPY");

    assertThat(paymentValidator.validate(request))
        .contains("Currency must be EUR, GBP, or USD");
  }

  @Test
  void rejectsANonPositiveAmount() {
    PaymentRequest request = validRequest();
    request.setAmount(0);

    assertThat(paymentValidator.validate(request))
        .contains("Amount must be a positive integer");
  }

  @Test
  void rejectsACvvThatIsNot3Or4Digits() {
    PaymentRequest request = validRequest();
    request.setCvv("12");

    assertThat(paymentValidator.validate(request)).contains("CVV must be 3 or 4 digits");
  }

  @Test
  void keepsALeadingZeroInTheCvv() {
    PaymentRequest request = validRequest();
    request.setCvv("0123");

    assertThat(paymentValidator.validate(request)).isEmpty();
  }

  @Test
  void reportsEveryMissingField() {
    List<String> errors = paymentValidator.validate(new PaymentRequest());

    assertThat(errors).containsExactly(
        "Card number must be 14 to 19 digits",
        "Expiry month must be between 1 and 12",
        "Expiry year is required",
        "Currency must be EUR, GBP, or USD",
        "Amount must be a positive integer",
        "CVV must be 3 or 4 digits");
  }

  private PaymentRequest validRequest() {
    PaymentRequest request = new PaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryMonth(4);
    request.setExpiryYear(2027);
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }
}
