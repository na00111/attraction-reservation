package com.example.attractionreservation.domain.routeRecommendation.preference;

import com.example.attractionreservation.domain.routeRecommendation.preference.port.JevPreferenceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PreferenceScorerTest {

    private static final AttractionProfile EXPRESS = new AttractionProfile(101L, "T익스프레스", "나무 롤러코스터");
    private static final AttractionProfile SAFARI = new AttractionProfile(102L, "사파리", "버스를 타고 동물을 본다");

    private final JevPreferenceClient jevClient = mock(JevPreferenceClient.class);
    private final PreferenceScorer scorer = new PreferenceScorer(new RuleBasedPreferenceScorer(), jevClient);

    @Test
    @DisplayName("취향 문장이 없으면 Jev를 부르지 않고 모두 보통으로 본다")
    void score_blankText_NeutralWithoutJev() {
        //when
        PreferenceScores result = scorer.score("  ", List.of(EXPRESS, SAFARI));

        //then
        assertThat(result.levels()).isEmpty();
        verify(jevClient, never()).score(anyString(), anyList());
    }

    @Test
    @DisplayName("규칙이 판단하지 못한 어트랙션만 Jev에게 묻는다")
    void score_partlyByRule_AskJevForRest() {
        //given
        String text = "T익스프레스는 꼭 타고 싶고, 동물은 별로예요";
        given(jevClient.score(eq(text), eq(List.of(SAFARI))))
                .willReturn(Optional.of(Map.of(102L, PreferenceLevel.DISLIKE)));

        //when
        PreferenceScores result = scorer.score(text, List.of(EXPRESS, SAFARI));

        //then
        assertThat(result.levelOf(101L)).isEqualTo(PreferenceLevel.LIKE);
        assertThat(result.levelOf(102L)).isEqualTo(PreferenceLevel.DISLIKE);
    }

    @Test
    @DisplayName("규칙이 모두 판단하면 Jev를 부르지 않는다")
    void score_allByRule_JevNotCalled() {
        //when
        scorer.score("T익스프레스는 좋은데 사파리는 싫어요", List.of(EXPRESS, SAFARI));

        //then
        verify(jevClient, never()).score(anyString(), anyList());
    }

    @Test
    @DisplayName("Jev가 물어보지 않은 어트랙션이나 null을 답하면 버린다")
    void score_jevAnswersOutsideRequest_Ignored() {
        //given: 규칙이 T익스프레스를 판단했으므로 Jev에게는 사파리만 묻는다
        Map<Long, PreferenceLevel> answer = new HashMap<>();
        answer.put(101L, PreferenceLevel.DISLIKE); // 묻지 않은 어트랙션(규칙 판단을 뒤집으려는 답)
        answer.put(999L, PreferenceLevel.LIKE);    // 요청에 없는 어트랙션
        answer.put(102L, null);
        given(jevClient.score(anyString(), anyList())).willReturn(Optional.of(answer));

        //when
        PreferenceScores result = scorer.score("T익스프레스는 꼭 타고 싶어요", List.of(EXPRESS, SAFARI));

        //then
        assertThat(result.levels()).containsOnly(Map.entry(101L, PreferenceLevel.LIKE));
    }

    @Test
    @DisplayName("Jev가 답하지 않으면 규칙이 판단하지 못한 어트랙션은 보통이다")
    void score_jevEmpty_Neutral() {
        //given
        given(jevClient.score(anyString(), anyList())).willReturn(Optional.empty());

        //when
        PreferenceScores result = scorer.score("스릴 있는 게 좋아요", List.of(EXPRESS, SAFARI));

        //then
        assertThat(result.levelOf(101L)).isEqualTo(PreferenceLevel.NEUTRAL);
        assertThat(result.levelOf(102L)).isEqualTo(PreferenceLevel.NEUTRAL);
    }

    @Test
    @DisplayName("Jev 구현체가 예외를 던져도 추천을 막지 않고 보통으로 본다")
    void score_jevThrows_Neutral() {
        //given
        given(jevClient.score(anyString(), any())).willThrow(new IllegalStateException("LLM timeout"));

        //when
        PreferenceScores result = scorer.score("스릴 있는 게 좋아요", List.of(EXPRESS, SAFARI));

        //then
        assertThat(result.levels()).isEmpty();
    }
}
