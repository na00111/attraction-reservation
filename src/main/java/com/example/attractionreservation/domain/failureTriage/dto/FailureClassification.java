package com.example.attractionreservation.domain.failureTriage.dto;

import com.example.attractionreservation.domain.failureTriage.entity.enums.AlertPriority;
import com.example.attractionreservation.domain.failureTriage.entity.enums.ClassifiedBy;
import com.example.attractionreservation.domain.failureTriage.entity.enums.Confidence;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory;

/**
 * 분류 결과.
 *
 * @param summary Jev가 남긴 한 줄 요약. 규칙 분류거나 Jev가 답하지 않았으면 null
 */
public record FailureClassification(
        FailureCategory category,
        Confidence confidence,
        ClassifiedBy classifiedBy,
        String summary
) {
    public static FailureClassification byRule(FailureCategory category, Confidence confidence) {
        return new FailureClassification(category, confidence, ClassifiedBy.RULE, null);
    }

    public static FailureClassification byJev(FailureCategory category, String summary) {
        return new FailureClassification(category, Confidence.HIGH, ClassifiedBy.JEV, summary);
    }

    /**
     * 규칙도 Jev도 확신하지 못한 경우. 추정한 분류는 참고로 남기되 우선순위는 즉시 확인이다.
     *
     * @param guess   규칙이나 Jev가 낮은 확신으로 고른 분류. 없으면 UNKNOWN
     * @param summary Jev의 요약이 있으면 알림에 함께 보낸다(원문 대신이 아니라 원문과 함께)
     */
    public static FailureClassification unsure(FailureCategory guess, String summary) {
        return new FailureClassification(guess, Confidence.LOW, ClassifiedBy.NONE, summary);
    }

    /**
     * 알림 우선순위. 확신이 낮으면 분류와 관계없이 즉시 확인으로 올린다.
     * 잘못 분류해 "대기"로 낮추는 것(진짜 데이터 오류를 놓침)이 잘못 올리는 것(운영자가 한 번 더 봄)보다 비싸기 때문이다.
     */
    public AlertPriority priority() {
        return confidence == Confidence.HIGH ? category.getPriority() : AlertPriority.CHECK_NOW;
    }
}
