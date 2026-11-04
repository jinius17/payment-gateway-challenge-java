package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.client.BankPaymentResult;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.InvalidPaymentException;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.validation.PaymentValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.client.ResourceAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  private PaymentsRepository paymentsRepository;
  @Mock
  private BankClient bankClient;

  private PaymentGatewayService paymentGatewayService;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);
    paymentGatewayService = new PaymentGatewayService(
        paymentsRepository, new PaymentValidator(clock), bankClient);
  }

  @Test
  void storesAnAuthorizedPaymentWithOnlyTheLastFourDigits() {
    PaymentRequest request = validRequest();
    when(bankClient.submit(request)).thenReturn(BankPaymentResult.from(true, "auth-code"));

    PostPaymentResponse payment = paymentGatewayService.processPayment(request);

    ArgumentCaptor<PostPaymentResponse> stored = ArgumentCaptor.forClass(PostPaymentResponse.class);
    verify(paymentsRepository).add(stored.capture());
    assertThat(stored.getValue()).isSameAs(payment);
    assertThat(payment.getId()).isNotNull();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(payment.getCardNumberLastFour()).isEqualTo(8877);
    assertThat(payment.getExpiryMonth()).isEqualTo(4);
    assertThat(payment.getExpiryYear()).isEqualTo(2027);
    assertThat(payment.getCurrency()).isEqualTo("GBP");
    assertThat(payment.getAmount()).isEqualTo(100);
  }

  @Test
  void storesADeclinedPayment() {
    PaymentRequest request = validRequest();
    when(bankClient.submit(request)).thenReturn(BankPaymentResult.from(false, ""));

    PostPaymentResponse payment = paymentGatewayService.processPayment(request);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    verify(paymentsRepository).add(payment);
  }

  @Test
  void rejectsAnInvalidPaymentWithoutCallingTheBank() {
    PaymentRequest request = validRequest();
    request.setAmount(0);

    assertThatThrownBy(() -> paymentGatewayService.processPayment(request))
        .isInstanceOf(InvalidPaymentException.class);

    verify(bankClient, never()).submit(any());
    verify(paymentsRepository, never()).add(any());
  }

  @Test
  void storesNothingWhenTheBankFails() {
    PaymentRequest request = validRequest();
    when(bankClient.submit(request)).thenThrow(new ResourceAccessException("Read timed out"));

    assertThatThrownBy(() -> paymentGatewayService.processPayment(request))
        .isInstanceOf(ResourceAccessException.class);

    verify(paymentsRepository, never()).add(any());
  }

  @Test
  void storesNothingWhenTheBankIsUnavailable() {
    PaymentRequest request = validRequest();
    when(bankClient.submit(request)).thenReturn(BankPaymentResult.unavailable());

    assertThatThrownBy(() -> paymentGatewayService.processPayment(request))
        .isInstanceOf(BankUnavailableException.class);

    verify(paymentsRepository, never()).add(any());
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
