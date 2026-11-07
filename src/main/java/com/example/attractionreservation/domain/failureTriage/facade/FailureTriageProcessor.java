package com.example.attractionreservation.domain.failureTriage.facade;

import com.example.attractionreservation.domain.failureTriage.classifier.FailureClassifier;
import com.example.attractionreservation.domain.failureTriage.dto.FailureAlert;
import com.example.attractionreservation.domain.failureTriage.dto.FailureClassification;
import com.example.attractionreservation.domain.failureTriage.dto.FailureContext;
import com.example.attractionreservation.domain.failureTriage.port.FailureAlertSender;
import com.example.attractionreservation.domain.failureTriage.service.FailureTriageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * DEAD가 된 결제·환불 실패를 분류해 운영자에게 알린다.
 * 트랜잭션 없이 "대상 조회(짧은 트랜잭션) → 분류(트랜잭션 밖, Jev 호출 가능) → 기록 저장(짧은 트랜잭션) → 알림" 순서로 진행한다.
 * RefundProcessor의 "점유 → PG 호출 → 결과 기록" 구조와 같은 이유로, 외부 호출을 트랜잭션 밖에 둔다.
 *
 * <p>지켜야 할 선: 이 클래스는 원본 행(Refund·PaymentCompensation·WebhookEvent)을 읽기만 하고 절대 바꾸지 않는다.
 * DEAD를 되살리거나 재시도를 예약하거나 환불을 승인하지 않는다. 돈이 오가는 결정은 기존 Processor의 결정적 코드가 한다.
 *
 * <p>알림은 "기록 저장 후 전송"이다. 여러 인스턴스가 같은 행을 분류해도 저장에 성공한 한 곳만 알림을 보내 중복 알림이 없다.
 * 대신 저장 후 전송이 실패하면 그 건의 알림은 다시 보내지 않는다(최대 한 번). 지금 알림은 로그라 실패할 일이 거의 없고,
 * 분류 기록은 남으므로 failure_triages에서 찾을 수 있다. 외부 알림으로 바꾸면 전송 여부 컬럼을 추가해 재전송을 검토한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FailureTriageProcessor {
    private final FailureTriageService failureTriageService;
    private final FailureClassifier failureClassifier;
    private final FailureAlertSender failureAlertSender;

    // 스케줄러에서 실행. 한 건이 실패해도 나머지는 계속 처리하고, 실패한 건은 기록이 없으므로 다음 주기에 다시 대상이 된다.
    public void triagePending(int limit) {
        for (FailureContext failure : failureTriageService.findUntriagedFailures(limit)) {
            try {
                triage(failure);
            } catch (RuntimeException e) {
                log.error("[FailureTriage] 분류 중 예기치 않은 오류: sourceType={}, sourceId={}",
                        failure.sourceType(), failure.sourceId(), e);
            }
        }
    }

    void triage(FailureContext failure) {
        FailureClassification classification = failureClassifier.classify(failure);

        boolean saved;
        try {
            saved = failureTriageService.saveTriage(failure, classification);
        } catch (DataIntegrityViolationException e) {
            // 존재 확인 뒤 다른 인스턴스가 먼저 저장함. 그쪽이 알림을 보낸다.
            saved = false;
        }

        if (!saved) {
            log.info("[FailureTriage] 다른 처리가 먼저 분류해 건너뜀: sourceType={}, sourceId={}",
                    failure.sourceType(), failure.sourceId());
            return;
        }

        failureAlertSender.send(FailureAlert.of(failure, classification));
    }
}
