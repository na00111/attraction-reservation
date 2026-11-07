package com.example.attractionreservation.domain.routeRecommendation.preference;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 취향 문장에서 어트랙션 이름을 직접 언급한 경우만 판단하는 규칙. Jev보다 먼저 실행한다.
 *
 * <p>예: "T익스프레스는 꼭 타고 싶어요, 사파리는 별로" → T익스프레스 LIKE, 사파리 DISLIKE
 *
 * <p>이름 언급만 보는 이유: 이름은 어트랙션마다 다른 고정 문자열이라, 문장에 들어 있으면 그 어트랙션을 가리킨다고 확신할 수 있다.
 * 반대로 설명(description)의 단어("물", "스릴")를 맞추는 규칙은 버렸다. "물 젖는 건 싫어"와 "물놀이 좋아"를 단어만으로는
 * 구분할 수 없고, 부정·조건 표현이 섞이면 틀린 답을 확신하게 된다. 그런 판단은 문맥을 읽는 Jev에게 맡긴다.
 *
 * <p>판단 방법: 문장을 쉼표·마침표·접속어로 절(clause)로 나누고, 어트랙션 이름이 들어 있는 절에
 * 긍정어만 있으면 LIKE, 부정어만 있으면 DISLIKE로 본다. 둘 다 있거나("꼭 빼 줘") 둘 다 없으면 판단하지 않는다.
 * 확실하지 않으면 답하지 않고 Jev에게 넘기는 것이 이 규칙의 원칙이다.
 */
@Component
public class RuleBasedPreferenceScorer {

    // 절을 나누는 기준. "A는 좋은데 B는 싫어"처럼 한 문장에 두 어트랙션이 있을 때 각각 따로 판단하려고 나눈다.
    // 연결 어미는 앞 글자 받침에 따라 "좋은데"/"타는데"처럼 은데·는데 둘 다 쓰인다.
    private static final Pattern CLAUSE_DELIMITER = Pattern.compile("[,.!?;\\n]|그리고|하지만|그런데|근데|[은는]데|지만");
    // "좋은데"가 절 나누기로 "좋은"만 남아도 잡히도록 "좋" 한 글자로 찾는다.
    // "안 좋아"는 부정어에도 걸려 둘 다 있는 절이 되므로 판단하지 않는다(잘못된 LIKE보다 판단 보류가 낫다).
    private static final Pattern POSITIVE = Pattern.compile("좋|꼭|제일|최고|타고 ?싶|타보고 ?싶");
    private static final Pattern NEGATIVE = Pattern.compile("싫|빼|제외|무서|별로|안 ?좋|좋지 ?않|안 ?탈|못 ?타|안 ?타");
    private static final int MIN_NAME_LENGTH = 2; // 한 글자 이름은 다른 단어에 우연히 섞이기 쉬워 판단하지 않는다

    /**
     * @return 이름이 언급되고 판단이 분명한 어트랙션만 담은 결과. 판단하지 않은 어트랙션은 키가 없다
     */
    public Map<Long, PreferenceLevel> score(String preferenceText, List<AttractionProfile> attractions) {
        Map<Long, PreferenceLevel> levels = new HashMap<>();
        if (preferenceText == null || preferenceText.isBlank()) {
            return levels;
        }

        String[] clauses = CLAUSE_DELIMITER.split(preferenceText);
        for (AttractionProfile attraction : attractions) {
            judge(attraction, clauses).ifPresent(level -> levels.put(attraction.attractionId(), level));
        }
        return levels;
    }

    // 이름이 나온 모든 절의 판단이 하나로 모일 때만 그 값을 쓴다. 절마다 다르면(앞에선 좋다, 뒤에선 싫다) 판단하지 않는다.
    private Optional<PreferenceLevel> judge(AttractionProfile attraction, String[] clauses) {
        String name = normalize(attraction.name());
        if (name.length() < MIN_NAME_LENGTH) {
            return Optional.empty();
        }

        PreferenceLevel judged = null;
        for (String clause : clauses) {
            if (!normalize(clause).contains(name)) {
                continue;
            }
            PreferenceLevel level = judgeClause(clause);
            if (level == null || (judged != null && judged != level)) {
                return Optional.empty();
            }
            judged = level;
        }
        return Optional.ofNullable(judged);
    }

    private PreferenceLevel judgeClause(String clause) {
        boolean positive = POSITIVE.matcher(clause).find();
        boolean negative = NEGATIVE.matcher(clause).find();
        if (positive == negative) {
            return null; // 둘 다 있거나 둘 다 없음
        }
        return positive ? PreferenceLevel.LIKE : PreferenceLevel.DISLIKE;
    }

    // "T 익스프레스"처럼 띄어 써도 같은 이름으로 보도록 공백을 지우고 대소문자를 맞춘다.
    private String normalize(String text) {
        return text.replaceAll("\\s+", "").toLowerCase();
    }
}
