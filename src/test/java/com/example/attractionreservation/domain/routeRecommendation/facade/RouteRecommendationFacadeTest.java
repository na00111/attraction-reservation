package com.example.attractionreservation.domain.routeRecommendation.facade;

import com.example.attractionreservation.domain.routeRecommendation.dto.CreateRouteRecommendationRequest;
import com.example.attractionreservation.domain.routeRecommendation.preference.AttractionProfile;
import com.example.attractionreservation.domain.routeRecommendation.preference.PreferenceLevel;
import com.example.attractionreservation.domain.routeRecommendation.preference.PreferenceScorer;
import com.example.attractionreservation.domain.routeRecommendation.preference.PreferenceScores;
import com.example.attractionreservation.domain.routeRecommendation.service.RouteRecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RouteRecommendationFacadeTest {

    private static final LocalDateTime START = LocalDateTime.parse("2026-10-20T13:00:00");
    private static final LocalDateTime END = LocalDateTime.parse("2026-10-20T18:00:00");

    private final RouteRecommendationService service = mock(RouteRecommendationService.class);
    private final PreferenceScorer scorer = mock(PreferenceScorer.class);
    private final RouteRecommendationFacade facade = new RouteRecommendationFacade(service, scorer);

    @Test
    @DisplayName("취향 문장이 없으면 어트랙션 조회와 취향 판단 없이 보통 점수로 추천한다")
    void create_noPreferenceText_NeutralScores() {
        //given
        CreateRouteRecommendationRequest request = new CreateRouteRecommendationRequest(1L, START, END, List.of(101L));

        //when
        facade.createRouteRecommendation(1L, request);

        //then
        verify(service, never()).findAttractionProfiles(any());
        verify(scorer, never()).score(anyString(), anyList());
        verify(service).createRouteRecommendation(1L, request, PreferenceScores.neutral());
    }

    @Test
    @DisplayName("취향 문장이 있으면 판단한 점수를 추천 계산에 넘기고, 판단은 추천 트랜잭션보다 먼저 한다")
    void create_withPreferenceText_ScoreThenCreate() {
        //given
        CreateRouteRecommendationRequest request =
                new CreateRouteRecommendationRequest(1L, START, END, List.of(101L), "스릴 좋아요");
        List<AttractionProfile> profiles = List.of(new AttractionProfile(101L, "T익스프레스", "롤러코스터"));
        PreferenceScores scores = new PreferenceScores(Map.of(101L, PreferenceLevel.LIKE));
        given(service.findAttractionProfiles(List.of(101L))).willReturn(profiles);
        given(scorer.score("스릴 좋아요", profiles)).willReturn(scores);

        //when
        facade.createRouteRecommendation(1L, request);

        //then
        InOrder order = inOrder(service, scorer);
        order.verify(service).findAttractionProfiles(List.of(101L));
        order.verify(scorer).score("스릴 좋아요", profiles);
        order.verify(service).createRouteRecommendation(1L, request, scores);
    }
}
