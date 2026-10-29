package com.example.attractionreservation.domain.failureTriage.entity.enums;

/**
 * 분류 확신도. 숫자 대신 두 단계로 둔다.
 * 확신도가 쓰이는 곳은 "분류 결과를 믿고 우선순위를 낮춰도 되는가" 하나뿐이라 두 단계면 충분하고,
 * LLM이 주는 0~1 점수는 보정(calibration)되지 않은 값이라 세밀한 숫자로 저장해도 의미가 없다.
 */
public enum Confidence {
    HIGH, // 분류 결과의 우선순위를 그대로 쓴다
    LOW   // 분류 결과를 참고로만 붙이고 원문과 함께 즉시 확인으로 보낸다
}
