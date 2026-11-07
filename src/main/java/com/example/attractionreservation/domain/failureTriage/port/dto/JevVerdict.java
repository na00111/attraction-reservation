package com.example.attractionreservation.domain.failureTriage.port.dto;

import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory;

/**
 * Jev(LLM)의 답. 구현체는 LLM 응답을 이 형태로 파싱해 돌려준다.
 * 선택지에 없는 분류를 답했거나 파싱할 수 없으면 답하지 않은 것으로(빈 값) 처리해야 한다.
 *
 * @param confidence Jev가 스스로 매긴 0~1 확신도. 보정되지 않은 값이므로 임계값 비교에만 쓴다
 * @param summary    운영자에게 보여줄 한 줄 요약
 */
public record JevVerdict(
        FailureCategory category,
        double confidence,
        String summary
) {
}
