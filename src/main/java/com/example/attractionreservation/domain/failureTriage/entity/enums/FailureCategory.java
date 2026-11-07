package com.example.attractionreservation.domain.failureTriage.entity.enums;

/**
 * 결제·환불 실패 원인 분류. Jev(LLM)에게 묻는 선택지와 규칙 분류기의 결과가 같은 값을 쓴다.
 * 선택지를 닫힌 enum으로 두는 이유: LLM이 자유 문장으로 답하면 알림 우선순위로 결정적으로 바꿀 수 없기 때문이다.
 */
public enum FailureCategory {
    PG_TEMPORARY_OUTAGE(AlertPriority.WAIT),     // PG 일시 장애: 타임아웃, 5xx, 통신 실패
    INVALID_REQUEST(AlertPriority.CHECK_NOW),    // 요청 데이터 오류: 금액 불일치, 없는 결제 등 다시 보내도 같은 거절
    ALREADY_CANCELLED(AlertPriority.CHECK),      // 이미 취소됨: PG에는 반영됐을 수 있어 원장과 대사가 필요
    UNKNOWN(AlertPriority.CHECK_NOW);            // 알 수 없음: 돈의 상태를 모르므로 가장 높은 우선순위

    private final AlertPriority priority;

    FailureCategory(AlertPriority priority) {
        this.priority = priority;
    }

    public AlertPriority getPriority() {
        return priority;
    }
}
