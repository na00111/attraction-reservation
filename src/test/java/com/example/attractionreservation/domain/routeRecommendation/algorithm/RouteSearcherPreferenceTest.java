package com.example.attractionreservation.domain.routeRecommendation.algorithm;

import com.example.attractionreservation.domain.routeRecommendation.preference.PreferenceLevel;
import com.example.attractionreservation.domain.routeRecommendation.preference.PreferenceScores;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 취향 점수가 경로 비교(isBetter 2순위)에 주는 영향만 확인한다. 기존 시간표 계산은 RouteSearcherTest가 맡는다.
class RouteSearcherPreferenceTest {
    private final RouteSearcher routeSearcher = new RouteSearcher();

    // 두 어트랙션 슬롯이 13:10~13:30으로 겹쳐 하나만 탈 수 있다. 취향이 없으면 ID가 작은 101을 고른다.
    private final CandidateAttraction express = attraction(101L, "T익스프레스", slot(1L, "13:10", "13:30"));
    private final CandidateAttraction safari = attraction(102L, "사파리", slot(2L, "13:10", "13:30"));

    @Test
    @DisplayName("취향이 없으면 기존 비교 규칙대로 고른다")
    void search_neutral_SameAsBefore() {
        //when
        RouteSearchResult withoutPreference = routeSearcher.search(at("13:00"), at("18:00"), List.of(express, safari));
        RouteSearchResult neutral = routeSearcher.search(
                at("13:00"), at("18:00"), List.of(express, safari), PreferenceScores.neutral());

        //then
        assertThat(visitedIds(withoutPreference)).containsExactly(101L);
        assertThat(visitedIds(neutral)).containsExactly(101L);
    }

    @Test
    @DisplayName("하나만 탈 수 있으면 좋아하는 어트랙션을 남긴다")
    void search_oneFits_KeepLiked() {
        //given
        PreferenceScores scores = scores(Map.of(102L, PreferenceLevel.LIKE));

        //when
        RouteSearchResult result = routeSearcher.search(at("13:00"), at("18:00"), List.of(express, safari), scores);

        //then
        assertThat(visitedIds(result)).containsExactly(102L);
        assertThat(result.unreachableAttractionIds()).containsExactly(101L);
    }

    @Test
    @DisplayName("하나만 탈 수 있으면 싫어하는 어트랙션을 먼저 뺀다")
    void search_oneFits_DropDisliked() {
        //given
        PreferenceScores scores = scores(Map.of(101L, PreferenceLevel.DISLIKE));

        //when
        RouteSearchResult result = routeSearcher.search(at("13:00"), at("18:00"), List.of(express, safari), scores);

        //then
        assertThat(visitedIds(result)).containsExactly(102L);
    }

    @Test
    @DisplayName("취향 점수는 총 소요 시간보다 우선한다")
    void search_likedButLonger_KeepLiked() {
        //given: 사파리는 30분 늦게 끝나지만 좋아함
        CandidateAttraction longSafari = attraction(102L, "사파리", slot(2L, "13:10", "14:00"));
        PreferenceScores scores = scores(Map.of(102L, PreferenceLevel.LIKE));

        //when
        RouteSearchResult result = routeSearcher.search(at("13:00"), at("14:00"), List.of(express, longSafari), scores);

        //then
        assertThat(visitedIds(result)).containsExactly(102L);
    }

    @Test
    @DisplayName("방문 개수가 많은 경로가 취향 점수보다 우선한다")
    void search_moreVisits_BeatsPreference() {
        //given: 회전목마(좋아함)를 타면 하나만, 나머지 둘을 타면 두 개를 탈 수 있다
        CandidateAttraction first = attraction(101L, "T익스프레스", slot(1L, "13:10", "13:30"));
        CandidateAttraction second = attraction(102L, "사파리", slot(2L, "13:40", "14:00"));
        CandidateAttraction carousel = attraction(103L, "회전목마", slot(3L, "13:10", "14:00"));
        PreferenceScores scores = scores(Map.of(103L, PreferenceLevel.LIKE));

        //when
        RouteSearchResult result = routeSearcher.search(
                at("13:00"), at("14:00"), List.of(first, second, carousel), scores);

        //then
        assertThat(visitedIds(result)).containsExactly(101L, 102L);
    }

    @Test
    @DisplayName("모두 탈 수 있으면 취향과 관계없이 기존과 같은 경로를 고른다")
    void search_allFit_PreferenceDoesNotChangeRoute() {
        //given
        CandidateAttraction first = attraction(101L, "T익스프레스", slot(1L, "13:10", "13:30"));
        CandidateAttraction second = attraction(102L, "사파리", slot(2L, "13:40", "14:00"));
        PreferenceScores scores = scores(Map.of(101L, PreferenceLevel.DISLIKE, 102L, PreferenceLevel.LIKE));

        //when
        RouteSearchResult neutral = routeSearcher.search(at("13:00"), at("18:00"), List.of(first, second));
        RouteSearchResult withPreference = routeSearcher.search(at("13:00"), at("18:00"), List.of(first, second), scores);

        //then: 같은 어트랙션 집합이라 취향 점수가 같고, 순서는 시간 기준으로 정해진다
        assertThat(visitedIds(withPreference)).isEqualTo(visitedIds(neutral));
    }

    private static PreferenceScores scores(Map<Long, PreferenceLevel> levels) {
        return new PreferenceScores(levels);
    }

    // 이동 시간은 비교에 영향이 없도록 모두 5분으로 둔다
    private static CandidateAttraction attraction(long id, String name, CandidateSlot... slots) {
        return new CandidateAttraction(id, name, 5, List.of(slots));
    }

    private static CandidateSlot slot(long slotId, String startHhmm, String endHhmm) {
        return new CandidateSlot(slotId, at(startHhmm), at(endHhmm));
    }

    private static LocalDateTime at(String hhmm) {
        return LocalDateTime.parse("2026-10-24T" + hhmm + ":00");
    }

    private static List<Long> visitedIds(RouteSearchResult result) {
        return result.visits().stream().map(PlannedVisit::attractionId).toList();
    }
}
