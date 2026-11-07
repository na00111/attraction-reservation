package com.example.attractionreservation.domain.failureTriage.entity.enums;

// 분류 대상이 된 실패가 어느 Outbox/Inbox 테이블의 행인지. (source_type, source_id)로 원본 행을 찾는다.
public enum FailureSourceType {
    REFUND,               // refunds: 환불 PG 부분 취소
    PAYMENT_COMPENSATION, // payment_compensations: 결제 실패 후 PG 보상 전체 취소
    WEBHOOK_EVENT         // webhook_events: PG 웹훅 처리
}
