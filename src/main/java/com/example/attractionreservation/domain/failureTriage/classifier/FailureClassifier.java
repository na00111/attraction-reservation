package com.example.attractionreservation.domain.failureTriage.classifier;

import com.example.attractionreservation.domain.failureTriage.dto.FailureClassification;
import com.example.attractionreservation.domain.failureTriage.dto.FailureContext;
import com.example.attractionreservation.domain.failureTriage.entity.enums.Confidence;
import com.example.attractionreservation.domain.failureTriage.port.JevClient;
import com.example.attractionreservation.domain.failureTriage.port.dto.JevVerdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 실패 원인을 "규칙 → Jev → 원문" 순서로 분류한다.
 *
 * <pre>
 * 규칙 HIGH ───────────────────────────────▶ 규칙 결과 (RULE, HIGH)
 * 규칙 LOW ─▶ Jev 확신도 ≥ 임계값 ─────────▶ Jev 결과  (JEV, HIGH)
 *           └▶ Jev 확신도 낮음·무응답·오류 ─▶ 추정만 참고 (NONE, LOW) → 우선순위 즉시 확인 + 원문
 * </pre>
 *
 * <p>이 클래스는 트랜잭션 밖에서만 호출한다. Jev 호출은 외부 API라 DB 락을 잡은 채 기다리면 안 되기 때문이다.
 * 분류 결과는 알림 우선순위에만 쓰이고, 환불·보상의 상태는 바꾸지 않는다.
 */
@Slf4j
@Component
public class FailureClassifier {
    private final RuleBasedFailureClassifier ruleBasedFailureClassifier;
    private final JevClient jevClient;
    private final double jevMinConfidence;

    public FailureClassifier(
            RuleBasedFailureClassifier ruleBasedFailureClassifier,
            JevClient jevClient,
            // Jev 답을 믿고 우선순위를 낮춰도 되는 최소 확신도. 실제 실패 데이터로 측정하기 전까지는 보수적으로 높게 둔다.
            @Value("${payment.failure-triage.jev.min-confidence:0.8}") double jevMinConfidence
    ) {
        this.ruleBasedFailureClassifier = ruleBasedFailureClassifier;
        this.jevClient = jevClient;
        this.jevMinConfidence = jevMinConfidence;
    }

    public FailureClassification classify(FailureContext context) {
        FailureClassification byRule = ruleBasedFailureClassifier.classify(context);
        if (byRule.confidence() == Confidence.HIGH) {
            return byRule;
        }

        Optional<JevVerdict> verdict = askJev(context);
        if (verdict.isEmpty()) {
            // Jev가 꺼져 있거나 답하지 못함. 규칙의 추정만 참고로 붙이고 원문 그대로 보낸다.
            return FailureClassification.unsure(byRule.category(), null);
        }

        JevVerdict answer = verdict.get();
        if (answer.confidence() >= jevMinConfidence) {
            return FailureClassification.byJev(answer.category(), answer.summary());
        }

        // Jev도 확신하지 못함. Jev의 추정과 요약은 운영자가 원문을 읽는 데 도움이 되므로 함께 보낸다.
        return FailureClassification.unsure(answer.category(), answer.summary());
    }

    // 포트 계약상 예외를 던지지 않아야 하지만, 구현체 버그로 던져도 알림은 나가야 하므로 여기서 한 번 더 막는다.
    private Optional<JevVerdict> askJev(FailureContext context) {
        try {
            return jevClient.classify(context)
                    .filter(answer -> answer.category() != null);
        } catch (RuntimeException e) {
            log.warn("[FailureTriage] Jev 분류 실패, 원문으로 알림: sourceType={}, sourceId={}",
                    context.sourceType(), context.sourceId(), e);
            return Optional.empty();
        }
    }
}
