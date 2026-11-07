package com.example.attractionreservation.domain.failureTriage.repository;

import com.example.attractionreservation.domain.failureTriage.dto.FailureClassification;
import com.example.attractionreservation.domain.failureTriage.dto.FailureContext;
import com.example.attractionreservation.domain.failureTriage.entity.FailureTriage;
import com.example.attractionreservation.domain.failureTriage.entity.enums.Confidence;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureSourceType;
import com.example.attractionreservation.domain.failureTriage.service.FailureTriageService;
import com.example.attractionreservation.domain.order.entity.Order;
import com.example.attractionreservation.domain.payment.entity.Payment;
import com.example.attractionreservation.domain.payment.entity.PaymentCompensation;
import com.example.attractionreservation.domain.payment.entity.WebhookEvent;
import com.example.attractionreservation.domain.refund.entity.Refund;
import com.example.attractionreservation.domain.refund.entity.enums.RefundStatus;
import com.example.attractionreservation.domain.user.entity.User;
import com.example.attractionreservation.global.config.JpaConfig;
import com.example.attractionreservation.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaConfig.class, QuerydslConfig.class, FailureTriageService.class})
class FailureTriageRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-29T01:00:00Z");
    private static final FailureClassification OUTAGE =
            FailureClassification.byRule(FailureCategory.PG_TEMPORARY_OUTAGE, Confidence.HIGH);

    @Autowired
    private FailureTriageService failureTriageService;

    @Autowired
    private FailureTriageRepository failureTriageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("분류하지 않은 DEAD 환불·보상·웹훅을 실패 사유와 함께 조회한다.")
    void findUntriagedFailures_deadRows_ReturnedWithLastError() {
        //given
        Refund refund = saveDeadRefund("Read timed out");
        PaymentCompensation compensation = saveDeadCompensation("pg-comp-1");
        WebhookEvent webhookEvent = saveDeadWebhook("wh-1", "pg-wh-1");
        flushAndClear();

        //when
        List<FailureContext> result = failureTriageService.findUntriagedFailures(10);

        //then
        assertThat(result).extracting(FailureContext::sourceType, FailureContext::sourceId, FailureContext::lastError)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(FailureSourceType.REFUND, refund.getId(), "Read timed out"),
                        org.assertj.core.groups.Tuple.tuple(FailureSourceType.PAYMENT_COMPENSATION, compensation.getId(), "결제 취소에 실패했습니다."),
                        org.assertj.core.groups.Tuple.tuple(FailureSourceType.WEBHOOK_EVENT, webhookEvent.getId(), "원장 상태와 충돌: 수동 확인 필요"));
        assertThat(result.get(0).pgId()).isEqualTo(refund.getPayment().getPgId());
    }

    @Test
    @DisplayName("DEAD가 아닌 행은 조회하지 않는다.")
    void findUntriagedFailures_notDead_Excluded() {
        //given
        Refund refund = saveDeadRefund("x");
        ReflectionTestUtils.setField(refund, "status", RefundStatus.FAILED);
        WebhookEvent webhookEvent = WebhookEvent.receive("wh-2", "pg-2", "Transaction.Paid", "{}");
        entityManager.persist(webhookEvent); // RECEIVED
        flushAndClear();

        //when & then
        assertThat(failureTriageService.findUntriagedFailures(10)).isEmpty();
    }

    @Test
    @DisplayName("이미 분류 기록이 있는 행은 다시 조회하지 않는다.")
    void findUntriagedFailures_alreadyTriaged_Excluded() {
        //given
        PaymentCompensation triaged = saveDeadCompensation("pg-comp-1");
        PaymentCompensation untriaged = saveDeadCompensation("pg-comp-2");
        flushAndClear();
        failureTriageService.saveTriage(FailureContext.from(entityManager.find(PaymentCompensation.class, triaged.getId())), OUTAGE);
        flushAndClear();

        //when
        List<FailureContext> result = failureTriageService.findUntriagedFailures(10);

        //then
        assertThat(result).extracting(FailureContext::sourceId).containsExactly(untriaged.getId());
    }

    @Test
    @DisplayName("종류별로 limit건까지만 조회한다.")
    void findUntriagedFailures_overLimit_LimitedPerType() {
        //given
        saveDeadCompensation("pg-comp-1");
        saveDeadCompensation("pg-comp-2");
        saveDeadCompensation("pg-comp-3");
        saveDeadWebhook("wh-1", "pg-wh-1");
        flushAndClear();

        //when
        List<FailureContext> result = failureTriageService.findUntriagedFailures(2);

        //then
        assertThat(result).extracting(FailureContext::sourceType).containsExactly(
                FailureSourceType.PAYMENT_COMPENSATION, FailureSourceType.PAYMENT_COMPENSATION, FailureSourceType.WEBHOOK_EVENT);
    }

    @Test
    @DisplayName("같은 행의 분류를 이미 저장했으면 저장하지 않고 false를 반환한다.")
    void saveTriage_alreadySaved_ReturnFalse() {
        //given
        FailureContext context = new FailureContext(FailureSourceType.REFUND, 1L, "pg-1", 5, "Read timed out");
        failureTriageService.saveTriage(context, OUTAGE);

        //when
        boolean saved = failureTriageService.saveTriage(context, OUTAGE);

        //then
        assertThat(saved).isFalse();
        assertThat(failureTriageRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("존재 확인을 지나쳐 같은 행을 두 번 저장하면 UNIQUE 제약이 막는다.")
    void save_duplicateSource_UniqueViolation() {
        //given
        FailureContext context = new FailureContext(FailureSourceType.REFUND, 1L, "pg-1", 5, "Read timed out");
        failureTriageRepository.saveAndFlush(FailureTriage.record(context, OUTAGE));

        //when & then
        assertThatThrownBy(() -> failureTriageRepository.saveAndFlush(FailureTriage.record(context, OUTAGE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("500자를 넘는 원문은 잘라서 저장한다.")
    void record_longError_Truncated() {
        //given
        FailureContext context = new FailureContext(FailureSourceType.REFUND, 1L, "pg-1", 5, "x".repeat(600));

        //when
        FailureTriage triage = failureTriageRepository.saveAndFlush(FailureTriage.record(context, OUTAGE));

        //then
        assertThat(triage.getRawError()).hasSize(500);
    }

    private Refund saveDeadRefund(String lastError) {
        User user = User.builder().email("test" + System.nanoTime() + "@email.com").password("password").nickname("name").build();
        entityManager.persist(user);
        Order order = Order.create(user, 10000, LocalDate.of(2026, 10, 29));
        entityManager.persist(order);
        Payment payment = Payment.create(order);
        payment.markAsPaid(NOW);
        entityManager.persist(payment);

        Refund refund = Refund.create(payment, "단순 변심", NOW);
        // 5번 실패한 상태를 엔티티 전이로 만들려면 PG 호출 흐름 전체가 필요해, 조회 조건에 필요한 값만 직접 넣는다.
        ReflectionTestUtils.setField(refund, "status", RefundStatus.DEAD);
        ReflectionTestUtils.setField(refund, "attemptCount", 5);
        ReflectionTestUtils.setField(refund, "lastError", lastError);
        entityManager.persist(refund);
        return refund;
    }

    // 엔티티 전이로 DEAD를 만든다: 5번 점유 후 실패 기록
    private PaymentCompensation saveDeadCompensation(String pgId) {
        PaymentCompensation compensation = PaymentCompensation.request(pgId, 10000L, "결제 실패 보상", NOW);
        for (int attempt = 0; attempt < 5; attempt++) {
            compensation.markAsProcessing();
        }
        compensation.markAsFailed("결제 취소에 실패했습니다.", NOW);
        entityManager.persist(compensation);
        return compensation;
    }

    private WebhookEvent saveDeadWebhook(String webhookId, String pgId) {
        WebhookEvent webhookEvent = WebhookEvent.receive(webhookId, pgId, "Transaction.Cancelled", "{}");
        webhookEvent.markAsProcessing();
        webhookEvent.markAsDead("원장 상태와 충돌: 수동 확인 필요", NOW);
        entityManager.persist(webhookEvent);
        return webhookEvent;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
