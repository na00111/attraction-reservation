package com.example.attractionreservation.domain.routeRecommendation.preference.port;

import com.example.attractionreservation.domain.routeRecommendation.preference.AttractionProfile;
import com.example.attractionreservation.domain.routeRecommendation.preference.PreferenceLevel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Jev(LLM)에게 사용자 취향 문장과 어트랙션 목록을 주고 어트랙션별 취향 단계를 묻는 포트.
 * 도메인은 인터페이스만 알고 구현은 infra에 둔다. LLM 제공자·프롬프트가 바뀌어도 경로 계산과 테스트는 그대로다.
 *
 * <p>구현체는 다음을 보장해야 한다.
 * <ul>
 *     <li>예외를 던지지 않는다. 타임아웃·파싱 실패는 빈 값으로 돌려준다. Jev가 죽어도 추천은 나가야 한다.</li>
 *     <li>짧은 시간 제한(예: 3초)을 건다. 사용자가 응답을 기다리는 추천 API 안에서 호출되기 때문이다.</li>
 *     <li>선택지는 {@code PreferenceLevel} 세 개로 닫아서 묻는다.</li>
 * </ul>
 * 받은 ID 밖의 키, null 값은 {@code PreferenceScorer}가 버리므로 구현체가 걸러내지 않아도 된다.
 *
 * @return 어트랙션 ID → 취향 단계. 꺼져 있거나 답하지 못했으면 빈 값
 */
public interface JevPreferenceClient {
    Optional<Map<Long, PreferenceLevel>> score(String preferenceText, List<AttractionProfile> attractions);
}
