package com.example.attractionreservation.infra.jev;

import com.example.attractionreservation.domain.failureTriage.dto.FailureContext;
import com.example.attractionreservation.domain.failureTriage.port.JevClient;
import com.example.attractionreservation.domain.failureTriage.port.dto.JevVerdict;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Jev 연동 전 기본 구현. 항상 답하지 않으므로 규칙이 확신하지 못한 실패는 원문 그대로 즉시 확인 알림으로 나간다.
 *
 * <p>payment.failure-triage.jev.enabled=true로 켜면 이 빈은 등록되지 않고, 같은 설정으로 켜지는 실제 LLM 구현체가 대신 등록된다.
 * 빈 존재 여부(@ConditionalOnMissingBean)가 아니라 설정값으로 고르는 이유: 컴포넌트 스캔 빈끼리는 등록 순서가 보장되지 않아
 * 어느 구현이 쓰일지 설정만 보고 알 수 없기 때문이다.
 * Jev 없이도 기능 전체가 동작하게 둔 이유: Jev는 분류 정확도를 올리는 선택 요소이고, 없다고 알림이 빠지면 안 되기 때문이다.
 */
@Component
@ConditionalOnProperty(name = "payment.failure-triage.jev.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledJevClient implements JevClient {

    @Override
    public Optional<JevVerdict> classify(FailureContext context) {
        return Optional.empty();
    }
}
