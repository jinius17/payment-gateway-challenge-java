package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import com.checkout.payment.gateway.validation.PaymentRequestReader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

  @Operation(summary = "Process a card payment",
      description = "Validates the payment, asks the acquiring bank for a decision, and stores "
          + "the masked result. An invalid payment is rejected and never reaches the bank.")
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "The bank decided. The status is Authorized or Declined.",
          content = @Content(schema = @Schema(implementation = PostPaymentResponse.class))),
      @ApiResponse(responseCode = "400",
          description = "Rejected. The request is invalid and the bank was not called.",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "415",
          description = "The Content-Type is not application/json.",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "500",
          description = "The bank request failed or timed out. Nothing was stored.",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "503",
          description = "The bank is unavailable. Nothing was stored.",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping(value = "/payment", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PostPaymentResponse> processPayment(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = PaymentRequest.class)))
      @RequestBody String body) {
    return new ResponseEntity<>(
        paymentGatewayService.processPayment(paymentRequestReader.read(body)), HttpStatus.OK);
  }

  @Operation(summary = "Retrieve a previously processed payment")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "The payment, with the card masked."),
      @ApiResponse(responseCode = "400", description = "The id is not a UUID.",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "404", description = "No payment has this id.",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/payment/{id}")
  public ResponseEntity<PostPaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }
}
