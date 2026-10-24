package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.model.PaymentRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class BankClient {

  private final RestTemplate restTemplate;
  private final String paymentsUrl;

  public BankClient(RestTemplate restTemplate,
      @Value("${bank.simulator.payments-url}") String paymentsUrl) {
    this.restTemplate = restTemplate;
    this.paymentsUrl = paymentsUrl;
  }

  public BankPaymentResult submit(PaymentRequest request) {
    try {
      BankWireResponse response = restTemplate.postForObject(
          paymentsUrl, payload(request), BankWireResponse.class);
      if (response == null) {
        return BankPaymentResult.unavailable();
      }
      return BankPaymentResult.from(response.isAuthorized(), response.getAuthorizationCode());
    } catch (HttpStatusCodeException exception) {
      if (exception.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        return BankPaymentResult.unavailable();
      }
      throw exception;
    }
  }

  private Map<String, Object> payload(PaymentRequest request) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("card_number", request.getCardNumber());
    body.put("expiry_date",
        String.format("%02d/%d", request.getExpiryMonth(), request.getExpiryYear()));
    body.put("currency", request.getCurrency());
    body.put("amount", request.getAmount());
    body.put("cvv", request.getCvv());
    return body;
  }

  static class BankWireResponse {

    private boolean authorized;
    @JsonProperty("authorization_code")
    private String authorizationCode;

    public boolean isAuthorized() {
      return authorized;
    }

    public void setAuthorized(boolean authorized) {
      this.authorized = authorized;
    }

    public String getAuthorizationCode() {
      return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
      this.authorizationCode = authorizationCode;
    }
  }
}
