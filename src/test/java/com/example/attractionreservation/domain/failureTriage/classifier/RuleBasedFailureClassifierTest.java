package com.example.attractionreservation.domain.failureTriage.classifier;

import com.example.attractionreservation.domain.failureTriage.dto.FailureClassification;
import com.example.attractionreservation.domain.failureTriage.dto.FailureContext;
import com.example.attractionreservation.domain.failureTriage.entity.enums.ClassifiedBy;
import com.example.attractionreservation.domain.failureTriage.entity.enums.Confidence;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureSourceType;
import com.example.attractionreservation.domain.failureTriage.fixture.FailureErrorSamples;
import com.example.attractionreservation.domain.failureTriage.fixture.FailureErrorSamples.Sample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedFailureClassifierTest {

    private static final java.util.List<Sample> SAMPLES = FailureErrorSamples.SAMPLES;

    private final RuleBasedFailureClassifier classifier = new RuleBasedFailureClassifier();

    @ParameterizedTest(name = "{0}")
    @FieldSource("SAMPLES")
    @DisplayName("테스트용 에러 샘플을 기대한 분류와 확신도로 분류한다.")
    void classify_errorSample_ExpectedCategory(Sample sample) {
        //when
        FailureClassification result = classifier.classify(context(sample.lastError()));

        //then
        assertThat(result.category()).isEqualTo(sample.category());
        assertThat(result.confidence()).isEqualTo(sample.confidence());
        assertThat(result.classifiedBy()).isEqualTo(ClassifiedBy.RULE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("실패 사유가 없으면 알 수 없음, 낮은 확신으로 분류한다.")
    void classify_blankError_UnknownLow(String lastError) {
        //when
        FailureClassification result = classifier.classify(context(lastError));

        //then
        assertThat(result.category()).isEqualTo(FailureCategory.UNKNOWN);
        assertThat(result.confidence()).isEqualTo(Confidence.LOW);
    }

    @Test
    @DisplayName("앞뒤 공백이 있어도 ErrorCode 메시지로 분류한다.")
    void classify_paddedErrorCodeMessage_Matched() {
        //when
        FailureClassification result = classifier.classify(context("  이미 취소된 결제입니다.\n"));

        //then
        assertThat(result.category()).isEqualTo(FailureCategory.ALREADY_CANCELLED);
        assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
    }

    private FailureContext context(String lastError) {
        return new FailureContext(FailureSourceType.REFUND, 1L, "pg-1", 5, lastError);
    }
}
