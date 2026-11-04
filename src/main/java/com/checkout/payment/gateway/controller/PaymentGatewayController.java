package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import com.checkout.payment.gateway.validation.PaymentRequestReader;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;
  private final PaymentRequestReader paymentRequestReader;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService,
      PaymentRequestReader paymentRequestReader) {
    this.paymentGatewayService = paymentGatewayService;
    this.paymentRequestReader = paymentRequestReader;
  }

  @PostMapping(value = "/payment", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PostPaymentResponse> processPayment(@RequestBody String body) {
    return new ResponseEntity<>(
        paymentGatewayService.processPayment(paymentRequestReader.read(body)), HttpStatus.OK);
  }

  @GetMapping("/payment/{id}")
  public ResponseEntity<PostPaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }
}
