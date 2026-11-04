package com.checkout.payment.gateway.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class PaymentsRepositoryTest {

  @Test
  void returnsAnEmptyResultForAnUnknownId() {
    assertThat(new PaymentsRepository().get(UUID.randomUUID())).isEmpty();
  }

  @Test
  void keepsEveryPaymentAddedFromManyThreads() throws Exception {
    PaymentsRepository repository = new PaymentsRepository();
    ExecutorService pool = Executors.newFixedThreadPool(8);
    List<UUID> ids = new ArrayList<>();
    List<Future<?>> writes = new ArrayList<>();
    try {
      for (int i = 0; i < 2000; i++) {
        PostPaymentResponse payment = new PostPaymentResponse();
        payment.setId(UUID.randomUUID());
        payment.setStatus(PaymentStatus.AUTHORIZED);
        ids.add(payment.getId());
        writes.add(pool.submit(() -> repository.add(payment)));
      }
      for (Future<?> write : writes) {
        write.get();
      }
    } finally {
      pool.shutdown();
    }

    assertThat(ids).allSatisfy(id -> assertThat(repository.get(id)).isPresent());
  }
}
