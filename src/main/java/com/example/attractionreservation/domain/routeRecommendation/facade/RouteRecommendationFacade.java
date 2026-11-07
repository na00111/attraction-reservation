package com.example.attractionreservation.domain.routeRecommendation.facade;

import com.example.attractionreservation.domain.routeRecommendation.dto.CreateRouteRecommendationRequest;
import com.example.attractionreservation.domain.routeRecommendation.dto.SearchRouteRecommendationResponse;
import com.example.attractionreservation.domain.routeRecommendation.preference.AttractionProfile;
import com.example.attractionreservation.domain.routeRecommendation.preference.PreferenceScorer;
import com.example.attractionreservation.domain.routeRecommendation.preference.PreferenceScores;
import com.example.attractionreservation.domain.routeRecommendation.service.RouteRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 추천 경로 생성. 트랜잭션 없이 "어트랙션 정보 조회(짧은 readOnly) → 취향 판단(트랜잭션 밖, Jev 호출 가능) → 추천 계산·저장(트랜잭션)" 순서로 진행한다.
 *
 * <p>Facade를 둔 이유: 기존 createRouteRecommendation은 메서드 전체가 하나의 트랜잭션이다. 그 안에서 Jev를 부르면
 * LLM 응답을 기다리는 동안 DB 커넥션을 잡고 있게 된다(컨벤션: 트랜잭션 안에서 외부 API 호출 금지).
 * 같은 클래스 안의 메서드를 나눠 부르는 방식은 프록시를 거치지 않아 트랜잭션 경계가 나뉘지 않으므로, 별도 빈으로 뺐다.
 * PaymentFacade와 같은 구성이다.
 */
@Component
@RequiredArgsConstructor
public class RouteRecommendationFacade {
    private final RouteRecommendationService routeRecommendationService;
    private final PreferenceScorer preferenceScorer;

    public SearchRouteRecommendationResponse createRouteRecommendation(Long userId, CreateRouteRecommendationRequest request) {
        PreferenceScores preferenceScores = scorePreference(request);
        return routeRecommendationService.createRouteRecommendation(userId, request, preferenceScores);
    }

    // 취향 문장이 없으면 어트랙션 조회도 Jev 호출도 하지 않는다. 기존 요청은 쿼리 한 번 늘지 않는다.
    private PreferenceScores scorePreference(CreateRouteRecommendationRequest request) {
        if (!request.hasPreferenceText()) {
            return PreferenceScores.neutral();
        }

        List<AttractionProfile> attractions = routeRecommendationService.findAttractionProfiles(request.attractionIds());
        return preferenceScorer.score(request.preferenceText(), attractions);
    }
}
