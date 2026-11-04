package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.client.BankPaymentResult;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.InvalidPaymentException;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.validation.PaymentValidator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final PaymentValidator paymentValidator;
  private final BankClient bankClient;

  public PaymentGatewayService(PaymentsRepository paymentsRepository,
      PaymentValidator paymentValidator, BankClient bankClient) {
    this.paymentsRepository = paymentsRepository;
    this.paymentValidator = paymentValidator;
    this.bankClient = bankClient;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Retrieving payment {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  public PostPaymentResponse processPayment(PaymentRequest paymentRequest) {
    List<String> errors = paymentValidator.validate(paymentRequest);
    if (!errors.isEmpty()) {
      throw new InvalidPaymentException(errors);
    }

    BankPaymentResult bankResult = bankClient.submit(paymentRequest);
    String lastFour = lastFourDigits(paymentRequest.getCardNumber());
    if (bankResult.isUnavailable()) {
      LOG.warn("Acquiring bank unavailable for card ending {}", lastFour);
      throw new BankUnavailableException();
    }

    PostPaymentResponse payment = toPayment(paymentRequest, bankResult.isAuthorized(), lastFour);
    paymentsRepository.add(payment);
    LOG.info("Payment {} {} for card ending {}", payment.getId(), payment.getStatus().getName(),
        lastFour);
    return payment;
  }

  private PostPaymentResponse toPayment(PaymentRequest paymentRequest, boolean authorized,
      String lastFour) {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setStatus(authorized ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED);
    payment.setCardNumberLastFour(lastFour);
    payment.setExpiryMonth(paymentRequest.getExpiryMonth());
    payment.setExpiryYear(paymentRequest.getExpiryYear());
    payment.setCurrency(paymentRequest.getCurrency());
    payment.setAmount(paymentRequest.getAmount());
    return payment;
  }

  private String lastFourDigits(String cardNumber) {
    return cardNumber.substring(cardNumber.length() - 4);
  }
}
