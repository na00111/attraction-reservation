package com.example.attractionreservation.domain.routeRecommendation.preference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedPreferenceScorerTest {

    private static final AttractionProfile EXPRESS = new AttractionProfile(101L, "T익스프레스", "나무 롤러코스터");
    private static final AttractionProfile SAFARI = new AttractionProfile(102L, "사파리", "버스를 타고 동물을 본다");
    private static final List<AttractionProfile> ATTRACTIONS = List.of(EXPRESS, SAFARI);

    private final RuleBasedPreferenceScorer scorer = new RuleBasedPreferenceScorer();

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "T익스프레스는 꼭 타고 싶어요 | LIKE",
            "T익스프레스 제일 좋아 | LIKE",
            "t 익스프레스 최고 | LIKE",
            "T익스프레스는 무서워요 | DISLIKE",
            "T익스프레스는 빼 주세요 | DISLIKE",
            "T익스프레스는 별로 | DISLIKE"
    })
    @DisplayName("이름을 언급한 절에 긍정어나 부정어만 있으면 그 단계로 판단한다")
    void score_nameWithSentiment_Judged(String text, PreferenceLevel expected) {
        //when
        Map<Long, PreferenceLevel> result = scorer.score(text, ATTRACTIONS);

        //then
        assertThat(result).containsExactly(Map.entry(101L, expected));
    }

    @Test
    @DisplayName("한 문장의 두 어트랙션을 절로 나눠 각각 판단한다")
    void score_twoClauses_JudgedSeparately() {
        //when
        Map<Long, PreferenceLevel> result = scorer.score("T익스프레스는 좋은데 사파리는 싫어요", ATTRACTIONS);

        //then
        assertThat(result).containsOnly(
                Map.entry(101L, PreferenceLevel.LIKE),
                Map.entry(102L, PreferenceLevel.DISLIKE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "T익스프레스는 안 좋아요",        // 긍정어(좋)와 부정어(안 좋)가 함께 있음
            "T익스프레스 꼭 빼 줘",           // 긍정어(꼭)와 부정어(빼)가 함께 있음
            "T익스프레스 타요",               // 긍정어도 부정어도 없음
            "T익스프레스 좋아, T익스프레스 싫어" // 절마다 판단이 다름
    })
    @DisplayName("판단이 분명하지 않으면 판단하지 않고 Jev에게 넘긴다")
    void score_ambiguous_NotJudged(String text) {
        //when & then
        assertThat(scorer.score(text, ATTRACTIONS)).isEmpty();
    }

    @Test
    @DisplayName("이름을 언급하지 않은 취향 문장은 설명과 단어가 겹쳐도 판단하지 않는다")
    void score_noNameMention_NotJudged() {
        //when: 설명의 "롤러코스터"와 겹치지만 이름 언급이 아니다
        Map<Long, PreferenceLevel> result = scorer.score("롤러코스터는 좋아요", ATTRACTIONS);

        //then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("한 글자 이름은 다른 단어에 섞이기 쉬워 판단하지 않는다")
    void score_singleCharacterName_NotJudged() {
        //given
        AttractionProfile shortName = new AttractionProfile(103L, "꼭", "짧은 이름");

        //when & then
        assertThat(scorer.score("꼭 좋아요", List.of(shortName))).isEmpty();
    }
}
