package com.example.attractionreservation.domain.failureTriage.entity.enums;

// 최종 분류를 누가 정했는지. 나중에 규칙과 Jev의 정확도를 따로 측정하기 위해 저장한다.
public enum ClassifiedBy {
    RULE, // 규칙 분류기(ErrorCode 메시지·예외 문구 매칭)
    JEV,  // 규칙이 확신하지 못해 Jev(LLM)에게 물었고 Jev가 높은 확신으로 답함
    NONE  // 규칙도 Jev도 확신하지 못함. 원문 그대로 운영자에게 보낸다
}
