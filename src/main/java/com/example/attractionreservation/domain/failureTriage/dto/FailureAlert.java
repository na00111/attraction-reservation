package com.example.attractionreservation.domain.failureTriage.dto;

import com.example.attractionreservation.domain.failureTriage.entity.enums.AlertPriority;

/**
 * 운영자에게 보내는 알림 한 건. 원문(lastError)은 확신도와 관계없이 항상 함께 보낸다.
 * 분류는 요약일 뿐이라, 운영자가 원문을 보고 분류가 틀렸음을 알아챌 수 있어야 한다.
 */
public record FailureAlert(
        FailureContext context,
        FailureClassification classification,
        AlertPriority priority
) {
    public static FailureAlert of(FailureContext context, FailureClassification classification) {
        return new FailureAlert(context, classification, classification.priority());
    }
}
