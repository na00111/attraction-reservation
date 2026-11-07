package com.example.attractionreservation.domain.routeRecommendation.preference;

import com.example.attractionreservation.domain.routeRecommendation.algorithm.PlannedVisit;

import java.util.List;
import java.util.Map;

/**
 * 어트랙션별 취향 단계. 경로의 취향 점수는 경로에 포함된 어트랙션 단계 점수의 합이다.
 *
 * <p>값이 없는 어트랙션은 NEUTRAL(0)로 본다. 그래서 Jev가 일부만 답해도, 답하지 않은 어트랙션이 불리해지지 않는다.
 */
public record PreferenceScores(Map<Long, PreferenceLevel> levels) {

    public PreferenceScores {
        levels = Map.copyOf(levels);
    }

    // 취향 문장이 없거나 판단하지 못한 경우. 모든 경로의 취향 점수가 같아져 기존 비교 규칙만 남는다.
    public static PreferenceScores neutral() {
        return new PreferenceScores(Map.of());
    }

    public PreferenceLevel levelOf(Long attractionId) {
        return levels.getOrDefault(attractionId, PreferenceLevel.NEUTRAL);
    }

    // 경로의 취향 점수 = 방문하는 어트랙션들의 단계 점수 합
    public int totalOf(List<PlannedVisit> visits) {
        return visits.stream()
                .mapToInt(visit -> levelOf(visit.attractionId()).getScore())
                .sum();
    }
}
