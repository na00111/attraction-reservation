package com.example.attractionreservation.domain.failureTriage.classifier;

import com.example.attractionreservation.domain.failureTriage.dto.FailureClassification;
import com.example.attractionreservation.domain.failureTriage.dto.FailureContext;
import com.example.attractionreservation.domain.failureTriage.entity.enums.AlertPriority;
import com.example.attractionreservation.domain.failureTriage.entity.enums.ClassifiedBy;
import com.example.attractionreservation.domain.failureTriage.entity.enums.Confidence;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureSourceType;
import com.example.attractionreservation.domain.failureTriage.port.JevClient;
import com.example.attractionreservation.domain.failureTriage.port.dto.JevVerdict;
import com.example.attractionreservation.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FailureClassifierTest {

    private static final double MIN_CONFIDENCE = 0.8;
    // 규칙이 HIGH로 분류하는 값과 LOW로 분류하는 값
    private static final String RULE_HIGH_ERROR = ErrorCode.PAYMENT_ALREADY_CANCELLED.getMessage();
    private static final String RULE_LOW_ERROR = ErrorCode.PAYMENT_CANCELLATION_FAILED.getMessage();

    private final JevClient jevClient = mock(JevClient.class);
    private final FailureClassifier classifier =
            new FailureClassifier(new RuleBasedFailureClassifier(), jevClient, MIN_CONFIDENCE);

    @Test
    @DisplayName("규칙이 높은 확신으로 분류하면 Jev에게 묻지 않는다.")
    void classify_ruleHigh_JevNotCalled() {
        //when
        FailureClassification result = classifier.classify(context(RULE_HIGH_ERROR));

        //then
        assertThat(result.classifiedBy()).isEqualTo(ClassifiedBy.RULE);
        assertThat(result.category()).isEqualTo(FailureCategory.ALREADY_CANCELLED);
        verify(jevClient, never()).classify(any());
    }

    @Test
    @DisplayName("규칙이 확신하지 못하고 Jev가 임계값 이상으로 답하면 Jev 분류와 우선순위를 쓴다.")
    void classify_jevConfident_JevResult() {
        //given
        given(jevClient.classify(any())).willReturn(Optional.of(
                new JevVerdict(FailureCategory.PG_TEMPORARY_OUTAGE, 0.9, "PG 5xx 반복")));

        //when
        FailureClassification result = classifier.classify(context(RULE_LOW_ERROR));

        //then
        assertThat(result.classifiedBy()).isEqualTo(ClassifiedBy.JEV);
        assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(result.priority()).isEqualTo(AlertPriority.WAIT);
        assertThat(result.summary()).isEqualTo("PG 5xx 반복");
    }

    @Test
    @DisplayName("Jev 확신도가 임계값과 같으면 Jev 분류를 쓴다.")
    void classify_jevAtThreshold_JevResult() {
        //given
        given(jevClient.classify(any())).willReturn(Optional.of(
                new JevVerdict(FailureCategory.PG_TEMPORARY_OUTAGE, MIN_CONFIDENCE, "요약")));

        //when
        FailureClassification result = classifier.classify(context(RULE_LOW_ERROR));

        //then
        assertThat(result.classifiedBy()).isEqualTo(ClassifiedBy.JEV);
    }

    @Test
    @DisplayName("Jev 확신도가 낮으면 추정과 요약은 남기되 즉시 확인으로 보낸다.")
    void classify_jevUnsure_CheckNowWithSummary() {
        //given
        given(jevClient.classify(any())).willReturn(Optional.of(
                new JevVerdict(FailureCategory.PG_TEMPORARY_OUTAGE, 0.5, "장애로 보이나 불확실")));

        //when
        FailureClassification result = classifier.classify(context(RULE_LOW_ERROR));

        //then
        assertThat(result.classifiedBy()).isEqualTo(ClassifiedBy.NONE);
        assertThat(result.category()).isEqualTo(FailureCategory.PG_TEMPORARY_OUTAGE);
        assertThat(result.summary()).isEqualTo("장애로 보이나 불확실");
        assertThat(result.priority()).isEqualTo(AlertPriority.CHECK_NOW);
    }

    @Test
    @DisplayName("Jev가 답하지 않으면 규칙의 추정으로 즉시 확인을 보낸다.")
    void classify_jevEmpty_RuleGuessCheckNow() {
        //given
        given(jevClient.classify(any())).willReturn(Optional.empty());

        //when
        FailureClassification result = classifier.classify(context(ErrorCode.PAYMENT_GATEWAY_ERROR.getMessage()));

        //then
        assertThat(result.classifiedBy()).isEqualTo(ClassifiedBy.NONE);
        assertThat(result.category()).isEqualTo(FailureCategory.PG_TEMPORARY_OUTAGE);
        assertThat(result.summary()).isNull();
        assertThat(result.priority()).isEqualTo(AlertPriority.CHECK_NOW);
    }

    @Test
    @DisplayName("Jev 구현체가 예외를 던져도 분류는 실패하지 않고 즉시 확인으로 보낸다.")
    void classify_jevThrows_CheckNow() {
        //given
        given(jevClient.classify(any())).willThrow(new IllegalStateException("LLM timeout"));

        //when
        FailureClassification result = classifier.classify(context(RULE_LOW_ERROR));

        //then
        assertThat(result.classifiedBy()).isEqualTo(ClassifiedBy.NONE);
        assertThat(result.priority()).isEqualTo(AlertPriority.CHECK_NOW);
    }

    @Test
    @DisplayName("Jev가 분류 없이 답하면 답하지 않은 것으로 본다.")
    void classify_jevNullCategory_TreatedAsEmpty() {
        //given
        given(jevClient.classify(any())).willReturn(Optional.of(new JevVerdict(null, 0.99, "분류 없음")));

        //when
        FailureClassification result = classifier.classify(context(RULE_LOW_ERROR));

        //then
        assertThat(result.classifiedBy()).isEqualTo(ClassifiedBy.NONE);
        assertThat(result.category()).isEqualTo(FailureCategory.UNKNOWN);
        assertThat(result.priority()).isEqualTo(AlertPriority.CHECK_NOW);
    }

    private FailureContext context(String lastError) {
        return new FailureContext(FailureSourceType.REFUND, 1L, "pg-1", 5, lastError);
    }
}
