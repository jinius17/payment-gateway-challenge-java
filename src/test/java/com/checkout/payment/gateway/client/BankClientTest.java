package com.checkout.payment.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.checkout.payment.gateway.model.PaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class BankClientTest {

  private static final String PAYMENTS_URL = "http://localhost:8080/payments";

  private MockRestServiceServer bank;
  private BankClient bankClient;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    bank = MockRestServiceServer.createServer(restTemplate);
    bankClient = new BankClient(restTemplate, PAYMENTS_URL);
  }

  @Test
  void returnsAuthorizedWhenTheBankApprovesTheCard() {
    bank.expect(requestTo(PAYMENTS_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""
            {
              "card_number": "2222405343248877",
              "expiry_date": "04/2027",
              "currency": "GBP",
              "amount": 100,
              "cvv": "123"
            }
            """))
        .andRespond(withSuccess(
            "{\"authorized\":true,\"authorization_code\":\"abc-123\"}",
            MediaType.APPLICATION_JSON));

    BankPaymentResult result = bankClient.submit(payment());

    assertThat(result.isAuthorized()).isTrue();
    assertThat(result.isUnavailable()).isFalse();
    assertThat(result.getAuthorizationCode()).isEqualTo("abc-123");
    bank.verify();
  }

  @Test
  void returnsDeclinedWhenTheBankRefusesTheCard() {
    bank.expect(requestTo(PAYMENTS_URL))
        .andRespond(withSuccess(
            "{\"authorized\":false,\"authorization_code\":\"\"}",
            MediaType.APPLICATION_JSON));

    BankPaymentResult result = bankClient.submit(payment());

    assertThat(result.isAuthorized()).isFalse();
    assertThat(result.isUnavailable()).isFalse();
  }

  @Test
  void returnsUnavailableWhenTheBankRespondsWith503() {
    bank.expect(requestTo(PAYMENTS_URL))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    BankPaymentResult result = bankClient.submit(payment());

    assertThat(result.isUnavailable()).isTrue();
    assertThat(result.isAuthorized()).isFalse();
  }

  private PaymentRequest payment() {
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
