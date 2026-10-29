package com.example.attractionreservation.domain.failureTriage.facade;

import com.example.attractionreservation.domain.failureTriage.classifier.FailureClassifier;
import com.example.attractionreservation.domain.failureTriage.dto.FailureAlert;
import com.example.attractionreservation.domain.failureTriage.dto.FailureClassification;
import com.example.attractionreservation.domain.failureTriage.dto.FailureContext;
import com.example.attractionreservation.domain.failureTriage.entity.enums.AlertPriority;
import com.example.attractionreservation.domain.failureTriage.entity.enums.Confidence;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureSourceType;
import com.example.attractionreservation.domain.failureTriage.port.FailureAlertSender;
import com.example.attractionreservation.domain.failureTriage.service.FailureTriageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FailureTriageProcessorTest {

    private static final FailureContext REFUND_FAILURE =
            new FailureContext(FailureSourceType.REFUND, 1L, "pg-1", 5, "Read timed out");
    private static final FailureContext COMPENSATION_FAILURE =
            new FailureContext(FailureSourceType.PAYMENT_COMPENSATION, 2L, "pg-2", 5, "결제 취소에 실패했습니다.");
    private static final FailureClassification OUTAGE =
            FailureClassification.byRule(FailureCategory.PG_TEMPORARY_OUTAGE, Confidence.HIGH);

    private final FailureTriageService failureTriageService = mock(FailureTriageService.class);
    private final FailureClassifier failureClassifier = mock(FailureClassifier.class);
    private final FailureAlertSender failureAlertSender = mock(FailureAlertSender.class);
    private final FailureTriageProcessor processor =
            new FailureTriageProcessor(failureTriageService, failureClassifier, failureAlertSender);

    @Test
    @DisplayName("분류 기록을 저장하면 분류 결과와 원문을 담아 알림을 보낸다.")
    void triage_saved_AlertSent() {
        //given
        given(failureClassifier.classify(REFUND_FAILURE)).willReturn(OUTAGE);
        given(failureTriageService.saveTriage(REFUND_FAILURE, OUTAGE)).willReturn(true);

        //when
        processor.triage(REFUND_FAILURE);

        //then
        ArgumentCaptor<FailureAlert> alert = ArgumentCaptor.forClass(FailureAlert.class);
        verify(failureAlertSender).send(alert.capture());
        assertThat(alert.getValue().priority()).isEqualTo(AlertPriority.WAIT);
        assertThat(alert.getValue().context().lastError()).isEqualTo("Read timed out");
    }

    @Test
    @DisplayName("다른 처리가 먼저 분류를 저장했으면 알림을 보내지 않는다.")
    void triage_alreadyTriaged_NoAlert() {
        //given
        given(failureClassifier.classify(REFUND_FAILURE)).willReturn(OUTAGE);
        given(failureTriageService.saveTriage(REFUND_FAILURE, OUTAGE)).willReturn(false);

        //when
        processor.triage(REFUND_FAILURE);

        //then
        verify(failureAlertSender, never()).send(any());
    }

    @Test
    @DisplayName("저장 중 다른 인스턴스와 UNIQUE 충돌이 나면 알림을 보내지 않는다.")
    void triage_uniqueConflict_NoAlert() {
        //given
        given(failureClassifier.classify(REFUND_FAILURE)).willReturn(OUTAGE);
        given(failureTriageService.saveTriage(REFUND_FAILURE, OUTAGE))
                .willThrow(new DataIntegrityViolationException("uk_failure_triages_source"));

        //when
        processor.triage(REFUND_FAILURE);

        //then
        verify(failureAlertSender, never()).send(any());
    }

    @Test
    @DisplayName("한 건 처리 중 예외가 나도 나머지 건은 계속 분류한다.")
    void triagePending_oneFails_OthersContinue() {
        //given
        given(failureTriageService.findUntriagedFailures(10)).willReturn(List.of(REFUND_FAILURE, COMPENSATION_FAILURE));
        given(failureClassifier.classify(REFUND_FAILURE)).willThrow(new IllegalStateException("boom"));
        given(failureClassifier.classify(COMPENSATION_FAILURE)).willReturn(OUTAGE);
        given(failureTriageService.saveTriage(COMPENSATION_FAILURE, OUTAGE)).willReturn(true);

        //when
        processor.triagePending(10);

        //then
        verify(failureAlertSender).send(any());
    }

    @Test
    @DisplayName("분류(Jev 호출 가능)를 마친 뒤 기록을 저장한다.")
    void triage_classifyThenSave_Ordered() {
        //given
        given(failureClassifier.classify(REFUND_FAILURE)).willReturn(OUTAGE);
        given(failureTriageService.saveTriage(REFUND_FAILURE, OUTAGE)).willReturn(true);

        //when
        processor.triage(REFUND_FAILURE);

        //then
        var inOrder = org.mockito.Mockito.inOrder(failureClassifier, failureTriageService);
        inOrder.verify(failureClassifier).classify(REFUND_FAILURE);
        inOrder.verify(failureTriageService).saveTriage(REFUND_FAILURE, OUTAGE);
    }
}
