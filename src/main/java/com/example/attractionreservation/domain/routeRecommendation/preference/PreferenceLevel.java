package com.example.attractionreservation.domain.routeRecommendation.preference;

/**
 * 어트랙션 하나에 대한 사용자 취향. Jev(LLM)에게 묻는 선택지와 규칙 판단이 같은 값을 쓴다.
 *
 * <p>0~100 같은 숫자 점수 대신 세 단계로 닫아 둔 이유:
 * LLM이 매기는 숫자는 같은 문장에도 호출마다 61, 58처럼 흔들린다. 그 작은 차이가 경로 비교에서
 * 총 소요 시간보다 앞서면, 같은 요청에 다른 경로가 나온다. 세 단계면 흔들려도 결과가 거의 바뀌지 않고,
 * 사용자에게 "왜 이 어트랙션이 빠졌는지"를 설명하기도 쉽다.
 */
public enum PreferenceLevel {
    LIKE(1),     // 좋아함: 일부만 넣을 수 있을 때 우선해서 남긴다
    NEUTRAL(0),  // 보통 또는 판단할 근거 없음. Jev가 꺼져 있으면 모두 이 값이라 기존 추천과 같아진다
    DISLIKE(-1); // 싫어함: 일부만 넣을 수 있을 때 먼저 뺀다. 그래도 추천에서 아예 제외하지는 않는다(직접 고른 어트랙션이므로)

    private final int score;

    PreferenceLevel(int score) {
        this.score = score;
    }

    public int getScore() {
        return score;
    }
}
