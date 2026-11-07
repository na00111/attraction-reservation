package com.example.attractionreservation.domain.routeRecommendation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record CreateRouteRecommendationRequest(
        @NotNull Long ticketId,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        @NotNull @Size(min = 1, max = 5) List<@NotNull Long> attractionIds,
        // 선택 입력. 예: "스릴 있는 건 좋은데 물 젖는 건 싫어요". 없으면 취향을 반영하지 않고 기존과 같이 추천한다.
        // 길이를 제한하는 이유: Jev(LLM)에 그대로 들어가는 자유 입력이라 비용·지연과 프롬프트 주입 여지를 줄이기 위해서다.
        @Size(max = 200) String preferenceText
) {
    // 취향 문장 없이 만드는 기존 호출을 그대로 쓸 수 있게 둔다.
    public CreateRouteRecommendationRequest(Long ticketId, LocalDateTime startAt, LocalDateTime endAt, List<Long> attractionIds) {
        this(ticketId, startAt, endAt, attractionIds, null);
    }

    public boolean hasPreferenceText() {
        return preferenceText != null && !preferenceText.isBlank();
    }
}
