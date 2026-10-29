package com.example.attractionreservation.domain.failureTriage.entity.enums;

/**
 * 운영자 알림의 우선순위. 분류 결과는 오직 이 값을 정하는 데만 쓴다.
 * 환불을 승인·취소하거나 재시도 여부를 바꾸는 데는 쓰지 않는다. 그 결정은 기존 상태 전이 코드가 한다.
 */
public enum AlertPriority {
    CHECK_NOW("즉시 확인"), // 사람이 보지 않으면 해결되지 않는 실패. 확신이 낮을 때도 여기로 보낸다
    CHECK("확인"),         // 돈은 이미 PG 기준으로 정리됐을 가능성이 높고 원장 대사만 필요한 실패
    WAIT("대기");          // PG 복구를 기다렸다가 일괄 확인하면 되는 실패

    private final String label;

    AlertPriority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
