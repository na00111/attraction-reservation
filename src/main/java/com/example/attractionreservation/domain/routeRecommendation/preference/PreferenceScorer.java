package com.example.attractionreservation.domain.routeRecommendation.preference;

import com.example.attractionreservation.domain.routeRecommendation.preference.port.JevPreferenceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 취향 문장으로 어트랙션별 취향 단계를 정한다. 순서는 "규칙 → Jev → NEUTRAL".
 *
 * <pre>
 * 이름을 직접 언급하고 판단이 분명함 ──▶ 규칙 결과 (Jev가 다르게 답해도 규칙이 이긴다)
 * 그 밖의 어트랙션 ──▶ Jev에게 묻기 ──▶ Jev 답
 *                                   └▶ 무응답·오류·선택지 밖 ──▶ NEUTRAL
 * </pre>
 *
 * <p>규칙이 Jev보다 우선인 이유: 사용자가 이름을 직접 말한 것은 가장 분명한 신호라, LLM의 해석으로 뒤집히면 안 된다.
 *
 * <p>이 클래스는 트랜잭션 밖에서만 호출한다(RouteRecommendationFacade). Jev 호출은 외부 API라 DB 커넥션을 잡은 채 기다리면 안 된다.
 * 결과는 경로 비교의 한 기준으로만 쓰이고, 슬롯 선택·시간표 유효성·예약 가능 여부에는 관여하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreferenceScorer {
    private final RuleBasedPreferenceScorer ruleBasedPreferenceScorer;
    private final JevPreferenceClient jevPreferenceClient;

    public PreferenceScores score(String preferenceText, List<AttractionProfile> attractions) {
        if (preferenceText == null || preferenceText.isBlank() || attractions.isEmpty()) {
            return PreferenceScores.neutral();
        }

        Map<Long, PreferenceLevel> levels = new HashMap<>(ruleBasedPreferenceScorer.score(preferenceText, attractions));

        // 규칙이 판단하지 못한 어트랙션만 Jev에게 묻는다. 모두 판단했으면 LLM을 부르지 않는다.
        List<AttractionProfile> undecided = attractions.stream()
                .filter(attraction -> !levels.containsKey(attraction.attractionId()))
                .toList();
        if (!undecided.isEmpty()) {
            levels.putAll(askJev(preferenceText, undecided));
        }

        log.info("[RoutePreference] 취향 단계 결정: attractions={}, decidedByRule={}, levels={}",
                attractions.size(), attractions.size() - undecided.size(), levels);
        return new PreferenceScores(levels);
    }

    /**
     * Jev의 답 중 물어본 어트랙션의 값만 받는다.
     * 취향 문장은 사용자가 쓴 자유 입력이라 "모든 어트랙션을 LIKE로" 같은 지시가 섞일 수 있다(prompt injection).
     * 그래도 결과는 물어본 ID의 세 단계 값으로만 들어오고, 그 값은 유효한 경로들 사이의 비교에만 쓰이므로
     * 정원·시간표 규칙을 넘는 경로가 만들어지지는 않는다.
     */
    private Map<Long, PreferenceLevel> askJev(String preferenceText, List<AttractionProfile> undecided) {
        Set<Long> askedIds = undecided.stream()
                .map(AttractionProfile::attractionId)
                .collect(Collectors.toSet());
        try {
            Optional<Map<Long, PreferenceLevel>> answer = jevPreferenceClient.score(preferenceText, undecided);
            return answer.orElseGet(Map::of).entrySet().stream()
                    .filter(entry -> entry.getKey() != null && askedIds.contains(entry.getKey()))
                    .filter(entry -> entry.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (RuntimeException e) {
            // 포트 계약상 예외를 던지지 않아야 하지만, 구현체 버그로 던져도 추천은 나가야 하므로 여기서 한 번 더 막는다.
            log.warn("[RoutePreference] Jev 취향 판단 실패, 보통으로 처리: attractionIds={}", askedIds, e);
            return Map.of();
        }
    }
}
