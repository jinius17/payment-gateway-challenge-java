package com.checkout.payment.gateway.controller;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.client.BankPaymentResult;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  private static final String PAYMENT = """
      {
        "cardNumber": "2222405343248877",
        "expiryMonth": 4,
        "expiryYear": 2035,
        "currency": "GBP",
        "amount": 100,
        "cvv": "123"
      }
      """;

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentsRepository paymentsRepository;
  @MockBean
  private BankClient bankClient;

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour("4321");

    paymentsRepository.add(payment);

    mvc.perform(get("/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(get("/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }

  @Test
  void whenPaymentIsAuthorizedThenMaskedPaymentIsReturned() throws Exception {
    when(bankClient.submit(any())).thenReturn(BankPaymentResult.from(true, "auth-code"));

    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(PAYMENT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8877"))
        .andExpect(jsonPath("$.expiryMonth").value(4))
        .andExpect(jsonPath("$.expiryYear").value(2035))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void whenPaymentIsInvalidThenItIsRejectedWithoutCallingTheBank() throws Exception {
    String invalidPayment = PAYMENT.replace("\"amount\": 100", "\"amount\": 0");

    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(invalidPayment))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Amount must be a positive integer"));

    verify(bankClient, never()).submit(any());
  }

  @Test
  void whenBankIsUnavailableThen503IsReturned() throws Exception {
    when(bankClient.submit(any())).thenReturn(BankPaymentResult.unavailable());

    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(PAYMENT))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("Acquiring bank is unavailable"));
  }

  @Test
  void whenPaymentIsAuthorizedThenItCanBeRetrieved() throws Exception {
    when(bankClient.submit(any())).thenReturn(BankPaymentResult.from(true, "auth-code"));

    String body = mvc.perform(post("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(PAYMENT))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
    String id = JsonPath.read(body, "$.id");

    mvc.perform(get("/payment/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8877"))
        .andExpect(jsonPath("$.expiryMonth").value(4))
        .andExpect(jsonPath("$.expiryYear").value(2035))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100));
  }

  @Test
  void whenBankReturnsAnErrorThen500IsReturned() throws Exception {
    when(bankClient.submit(any()))
        .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(PAYMENT))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("Acquiring bank request failed"))
        .andExpect(jsonPath("$.status").doesNotExist());
  }

  @Test
  void whenBankTimesOutThen500IsReturned() throws Exception {
    when(bankClient.submit(any())).thenThrow(new ResourceAccessException("Read timed out"));

    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(PAYMENT))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("Acquiring bank request failed"));
  }

  @Test
  void whenAmountIsFractionalThenItIsRejectedWithoutCallingTheBank() throws Exception {
    String fractional = PAYMENT.replace("\"amount\": 100", "\"amount\": 10.5");

    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(fractional))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Amount must be a positive integer"));

    verify(bankClient, never()).submit(any());
  }

  @Test
  void whenAmountDoesNotFitInAnIntegerThenItIsRejected() throws Exception {
    String tooLarge = PAYMENT.replace("\"amount\": 100", "\"amount\": 99999999999");

    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(tooLarge))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Amount must be a positive integer"));

    verify(bankClient, never()).submit(any());
  }

  @Test
  void whenCardNumberIsAJsonNumberThenItIsRejected() throws Exception {
    String numericCard = PAYMENT.replace(
        "\"cardNumber\": \"2222405343248877\"", "\"cardNumber\": 2222405343248877");

    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(numericCard))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Card number must be 14 to 19 digits"));

    verify(bankClient, never()).submit(any());
  }

  @Test
  void whenExpiryYearIsNotFourDigitsThenItIsRejected() throws Exception {
    String hugeYear = PAYMENT.replace("\"expiryYear\": 2035", "\"expiryYear\": 2000000000");

    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(hugeYear))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Expiry year must be a four-digit year"));

    verify(bankClient, never()).submit(any());
  }

  @Test
  void whenTheBodyIsNotJsonThenItIsRejected() throws Exception {
    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Payment request could not be read"));

    verify(bankClient, never()).submit(any());
  }

  @Test
  void whenThePaymentIdIsNotAUuidThen400IsReturned() throws Exception {
    mvc.perform(get("/payment/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Payment id must be a UUID"))
        .andExpect(jsonPath("$.status").doesNotExist());
  }

  @Test
  void whenTheCardEndsWithALeadingZeroThenTheLastFourDigitsArePreserved() throws Exception {
    when(bankClient.submit(any())).thenReturn(BankPaymentResult.from(true, "auth-code"));
    String card = PAYMENT.replace("2222405343248877", "2222405343240012");

    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(card))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cardNumberLastFour").value("0012"));
  }
}
