package com.example.attractionreservation.domain.failureTriage.dto;

import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureSourceType;
import com.example.attractionreservation.domain.payment.entity.PaymentCompensation;
import com.example.attractionreservation.domain.payment.entity.WebhookEvent;
import com.example.attractionreservation.domain.refund.entity.Refund;

/**
 * 분류기에 넘기는 실패 한 건의 정보. 트랜잭션 안에서 엔티티를 읽어 값만 꺼내고,
 * 분류(Jev 호출 포함)는 트랜잭션 밖에서 이 값으로 한다.
 *
 * <p>Jev에게 보내는 값도 이것뿐이다. 사용자 이름·연락처 같은 개인정보는 세 엔티티에 없으므로 넘어가지 않는다.
 *
 * @param pgId      로그·알림에서 PG 관리자 화면을 찾아보기 위한 값. 결제와 무관한 웹훅이면 null
 * @param lastError 마지막 실패 사유. 현재는 PG 응답 원문이 아니라 대부분 ErrorCode 메시지다(README 참고)
 */
public record FailureContext(
        FailureSourceType sourceType,
        Long sourceId,
        String pgId,
        int attemptCount,
        String lastError
) {
    public static FailureContext from(Refund refund) {
        return new FailureContext(FailureSourceType.REFUND, refund.getId(),
                refund.getPayment().getPgId(), refund.getAttemptCount(), refund.getLastError());
    }

    public static FailureContext from(PaymentCompensation compensation) {
        return new FailureContext(FailureSourceType.PAYMENT_COMPENSATION, compensation.getId(),
                compensation.getPgId(), compensation.getAttemptCount(), compensation.getLastError());
    }

    public static FailureContext from(WebhookEvent webhookEvent) {
        return new FailureContext(FailureSourceType.WEBHOOK_EVENT, webhookEvent.getId(),
                webhookEvent.getPgId(), webhookEvent.getAttemptCount(), webhookEvent.getLastError());
    }
}
