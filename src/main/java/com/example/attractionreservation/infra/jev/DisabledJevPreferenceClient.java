package com.example.attractionreservation.infra.jev;

import com.example.attractionreservation.domain.routeRecommendation.preference.AttractionProfile;
import com.example.attractionreservation.domain.routeRecommendation.preference.PreferenceLevel;
import com.example.attractionreservation.domain.routeRecommendation.preference.port.JevPreferenceClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Jev 연동 전 기본 구현. 항상 답하지 않으므로, 취향 문장에서 어트랙션 이름을 직접 언급한 경우(규칙 판단)만 반영되고
 * 나머지는 NEUTRAL이 된다.
 *
 * <p>route.preference.jev.enabled=true로 켜면 이 빈은 등록되지 않고, 같은 설정으로 켜지는 실제 구현체가 대신 등록된다.
 * 빈 존재 여부가 아니라 설정값으로 고르는 이유: 컴포넌트 스캔 빈끼리는 등록 순서가 보장되지 않기 때문이다.
 */
@Component
@ConditionalOnProperty(name = "route.preference.jev.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledJevPreferenceClient implements JevPreferenceClient {

    @Override
    public Optional<Map<Long, PreferenceLevel>> score(String preferenceText, List<AttractionProfile> attractions) {
        return Optional.empty();
    }
}
