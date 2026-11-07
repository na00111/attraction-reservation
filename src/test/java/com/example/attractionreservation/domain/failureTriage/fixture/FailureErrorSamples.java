package com.example.attractionreservation.domain.failureTriage.fixture;

import com.example.attractionreservation.domain.failureTriage.entity.enums.Confidence;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory;
import com.example.attractionreservation.global.exception.ErrorCode;

import java.util.List;

import static com.example.attractionreservation.domain.failureTriage.entity.enums.Confidence.HIGH;
import static com.example.attractionreservation.domain.failureTriage.entity.enums.Confidence.LOW;
import static com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory.ALREADY_CANCELLED;
import static com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory.INVALID_REQUEST;
import static com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory.PG_TEMPORARY_OUTAGE;
import static com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory.UNKNOWN;

/**
 * 테스트용 last_error 샘플과 기대 분류.
 *
 * <p>개발 단계에서는 실제 DEAD 건수가 적어 분류기를 실제 데이터로 잴 수 없다. 그래서 코드가 실제로 남기는 문자열
 * (ErrorCode 메시지, Processor 사유 문구)과 ErrorCode로 감싸지지 않은 예외 원문 예시를 모아 "정답지"로 쓴다.
 * 운영에서 새 문구가 나오면 failure_triages의 raw_error를 보고 여기에 추가한 뒤 규칙을 고친다.
 */
public final class FailureErrorSamples {

    public record Sample(String description, String lastError, FailureCategory category, Confidence confidence) {
        @Override
        public String toString() {
            return description; // 파라미터 테스트 이름으로 쓴다
        }
    }

    public static final List<Sample> SAMPLES = List.of(
            // ① ErrorCode 메시지: PortOneClient·Processor가 BusinessException으로 던진 값이 그대로 last_error가 된다
            new Sample("이미 취소된 결제", ErrorCode.PAYMENT_ALREADY_CANCELLED.getMessage(), ALREADY_CANCELLED, HIGH),
            new Sample("취소 가능 금액 불일치", ErrorCode.PAYMENT_CANCELLABLE_AMOUNT_MISMATCH.getMessage(), ALREADY_CANCELLED, HIGH),
            new Sample("결제 금액 원장 불일치", ErrorCode.PAYMENT_INCORRECT_AMOUNT.getMessage(), INVALID_REQUEST, HIGH),
            new Sample("PG 결제 없음", ErrorCode.PAYMENT_PG_PAYMENT_NOT_FOUND.getMessage(), INVALID_REQUEST, HIGH),
            new Sample("PG 조회 실패(대부분 장애지만 4xx도 섞임)", ErrorCode.PAYMENT_GATEWAY_ERROR.getMessage(), PG_TEMPORARY_OUTAGE, LOW),
            new Sample("PG 취소 실패(4xx 거절과 통신 소진이 같은 문구)", ErrorCode.PAYMENT_CANCELLATION_FAILED.getMessage(), UNKNOWN, LOW),

            // ② Processor·CommandService가 직접 넣는 사유 문구
            new Sample("PG가 취소 실패로 확정", "PG 취소 실패 확인", INVALID_REQUEST, LOW),
            new Sample("PG 취소 확인 24시간 초과", "PG 취소 확인 기한 초과", UNKNOWN, LOW),
            new Sample("웹훅 원장 충돌", "원장 상태와 충돌: 수동 확인 필요", INVALID_REQUEST, HIGH),
            new Sample("웹훅 환불 금액 불일치", "PG 취소 금액과 환불 불일치: 수동 확인 필요", INVALID_REQUEST, HIGH),

            // ③ ErrorCode로 감싸지지 않은 예외 원문 예시
            new Sample("읽기 타임아웃", "I/O error on POST request for \"https://api.portone.io/payments/x/cancel\": Read timed out",
                    PG_TEMPORARY_OUTAGE, HIGH),
            new Sample("연결 거부", "Connection refused", PG_TEMPORARY_OUTAGE, HIGH),
            new Sample("PG 503", "503 Service Unavailable from POST https://api.portone.io", PG_TEMPORARY_OUTAGE, HIGH),
            new Sample("PG 400", "400 Bad Request: {\"type\":\"INVALID_REQUEST\"}", INVALID_REQUEST, HIGH),
            new Sample("영문 이미 취소", "{\"type\":\"PAYMENT_ALREADY_CANCELLED\"}", ALREADY_CANCELLED, HIGH),

            // ④ 알 수 없음: 규칙이 확신하지 못해 Jev나 운영자가 봐야 하는 값
            // "timeout"이 들어 있지만 PG가 아니라 우리 DB 문제다. PG 장애(대기)로 낮아지면 안 된다
            new Sample("DB 락 대기 초과", "could not execute statement; Lock wait timeout exceeded", UNKNOWN, LOW),
            new Sample("NPE", "Cannot invoke \"java.lang.Long.longValue()\" because \"amount\" is null", UNKNOWN, LOW),
            new Sample("ID에 503이 섞인 문구", "refund-5031 처리 실패", UNKNOWN, LOW)
    );

    private FailureErrorSamples() {
    }
}
