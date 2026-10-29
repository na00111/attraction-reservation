package com.example.attractionreservation.domain.failureTriage.entity;

import com.example.attractionreservation.domain.failureTriage.dto.FailureClassification;
import com.example.attractionreservation.domain.failureTriage.entity.enums.AlertPriority;
import com.example.attractionreservation.domain.failureTriage.entity.enums.Confidence;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class FailureClassificationTest {

    @ParameterizedTest
    @CsvSource({
            "PG_TEMPORARY_OUTAGE, WAIT",
            "INVALID_REQUEST, CHECK_NOW",
            "ALREADY_CANCELLED, CHECK",
            "UNKNOWN, CHECK_NOW"
    })
    @DisplayName("확신이 높으면 분류에 정해진 우선순위를 쓴다.")
    void priority_highConfidence_CategoryPriority(FailureCategory category, AlertPriority expected) {
        //given
        FailureClassification classification = FailureClassification.byRule(category, Confidence.HIGH);

        //when & then
        assertThat(classification.priority()).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(FailureCategory.class)
    @DisplayName("확신이 낮으면 어떤 분류든 즉시 확인으로 올린다.")
    void priority_lowConfidence_AlwaysCheckNow(FailureCategory category) {
        //given
        FailureClassification classification = FailureClassification.byRule(category, Confidence.LOW);

        //when & then
        assertThat(classification.priority()).isEqualTo(AlertPriority.CHECK_NOW);
    }
}
