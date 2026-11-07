package com.example.attractionreservation.domain.routeRecommendation.preference;

// 취향 판단에 쓰는 어트랙션 정보. Jev에게 보내는 값도 이것뿐이다(사용자 정보는 보내지 않는다).
public record AttractionProfile(
        Long attractionId,
        String name,
        String description
) {
}
