package com.example.attractionreservation.domain.failureTriage.classifier;

import com.example.attractionreservation.domain.failureTriage.dto.FailureClassification;
import com.example.attractionreservation.domain.failureTriage.dto.FailureContext;
import com.example.attractionreservation.domain.failureTriage.entity.enums.Confidence;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory;
import com.example.attractionreservation.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.example.attractionreservation.domain.failureTriage.entity.enums.Confidence.HIGH;
import static com.example.attractionreservation.domain.failureTriage.entity.enums.Confidence.LOW;
import static com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory.ALREADY_CANCELLED;
import static com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory.INVALID_REQUEST;
import static com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory.PG_TEMPORARY_OUTAGE;
import static com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory.UNKNOWN;

/**
 * last_error 문자열로 실패 원인을 분류하는 규칙 분류기. Jev보다 먼저 실행하는 1단계다.
 *
 * <p>규칙을 먼저 두는 이유: 현재 last_error 대부분은 BusinessException의 ErrorCode 메시지라 값의 종류가 적고 고정돼 있다.
 * 이런 값은 LLM 없이도 항상 같은 답을 낼 수 있고, 같은 입력에 같은 답을 내는 쪽이 테스트하고 설명하기 쉽다.
 * Jev는 규칙이 확신하지 못한 문자열(여러 원인이 한 메시지로 합쳐진 경우, 예상 못 한 예외 문구)에만 묻는다.
 *
 * <p>판단 순서: ① ErrorCode 메시지 정확히 일치 → ② 처리 코드가 직접 남긴 사유 문구 → ③ 예외 원문 키워드 → ④ 알 수 없음.
 * 앞 단계일수록 문자열이 어디서 왔는지 확실하므로 먼저 본다.
 */
@Component
public class RuleBasedFailureClassifier {

    /**
     * ① ErrorCode 메시지와 정확히 일치하는 경우. 문구를 복사하지 않고 ErrorCode에서 꺼내므로 메시지가 바뀌어도 규칙이 따라간다.
     *
     * <p>LOW로 둔 두 항목이 이 기능이 Jev를 필요로 하는 이유다.
     * <ul>
     *     <li>PAYMENT_CANCELLATION_FAILED: PortOneClient가 PG의 4xx 거절(데이터 오류)과 통신 재시도 소진(일시 장애)을
     *     모두 이 코드로 던진다. 문자열만으로는 둘을 구분할 수 없다.</li>
     *     <li>PAYMENT_GATEWAY_ERROR: 대부분 PG 5xx·통신 실패지만, API 키 오류 같은 404 외 4xx도 같은 코드다.</li>
     * </ul>
     */
    private static final Map<String, FailureClassification> BY_ERROR_CODE_MESSAGE = Map.of(
            // 다른 경로로 이미 전체 취소됨. RefundProcessor가 재시도하지 않고 바로 DEAD로 보내는 실패다
            ErrorCode.PAYMENT_ALREADY_CANCELLED.getMessage(), rule(ALREADY_CANCELLED, HIGH),
            // 앞선 취소가 PG에 반영돼 취소 가능 금액이 달라진 경우라 "이미 취소됨"과 같이 대사가 필요하다
            ErrorCode.PAYMENT_CANCELLABLE_AMOUNT_MISMATCH.getMessage(), rule(ALREADY_CANCELLED, HIGH),
            ErrorCode.PAYMENT_INVALID_AMOUNT.getMessage(), rule(INVALID_REQUEST, HIGH),
            ErrorCode.PAYMENT_INCORRECT_AMOUNT.getMessage(), rule(INVALID_REQUEST, HIGH),
            ErrorCode.PAYMENT_NOT_FOUND.getMessage(), rule(INVALID_REQUEST, HIGH),
            ErrorCode.PAYMENT_PG_PAYMENT_NOT_FOUND.getMessage(), rule(INVALID_REQUEST, HIGH),
            ErrorCode.PAYMENT_GATEWAY_ERROR.getMessage(), rule(PG_TEMPORARY_OUTAGE, LOW),
            ErrorCode.PAYMENT_CANCELLATION_FAILED.getMessage(), rule(UNKNOWN, LOW)
    );

    /**
     * ② 처리 코드(Processor·CommandService·엔티티)가 직접 넣는 사유 문구. 해당 코드의 private 상수라 참조하지 못해 문구로 맞춘다.
     * 문구가 바뀌면 이 규칙은 ④(알 수 없음, LOW)로 떨어질 뿐 잘못 낮은 우선순위가 되지는 않는다.
     */
    private static final Map<String, FailureClassification> BY_PROCESSOR_REASON = Map.of(
            // PG가 취소를 접수했다가 최종 실패로 확정. 같은 요청을 다시 보내도 거절될 가능성이 높지만 원인은 모른다
            "PG 취소 실패 확인", rule(INVALID_REQUEST, LOW),
            // PG가 24시간 동안 취소를 확정하지 않음. PG 쪽 문제일 수도, 취소가 사라졌을 수도 있다
            "PG 취소 확인 기한 초과", rule(UNKNOWN, LOW),
            // 웹훅 처리 결과가 원장과 충돌해 결정적 코드가 이미 "수동 확인 필요"로 판단한 경우
            "원장 상태와 충돌: 수동 확인 필요", rule(INVALID_REQUEST, HIGH),
            "PG 취소 금액과 환불 불일치: 수동 확인 필요", rule(INVALID_REQUEST, HIGH)
    );

    /**
     * ③ ErrorCode로 감싸지지 않은 예외의 원문(e.getMessage())에서 찾는 키워드. 위에서부터 처음 맞는 규칙을 쓴다.
     * 숫자 상태 코드는 ID 같은 다른 숫자와 섞이지 않도록 단어 경계(\b)로 찾는다.
     */
    private static final List<KeywordRule> KEYWORD_RULES = List.of(
            // 우리 DB 오류. "Lock wait timeout"이 아래 타임아웃 규칙에 걸려 PG 장애(대기)로 낮아지지 않도록 가장 먼저 본다.
            // PG가 아니라 우리 쪽 문제라 원인을 단정하지 않고 Jev·운영자에게 넘긴다.
            new KeywordRule(Pattern.compile("lock wait|deadlock|could not execute statement|jdbc", Pattern.CASE_INSENSITIVE),
                    rule(UNKNOWN, LOW)),
            new KeywordRule(Pattern.compile("already[ _]?cancel", Pattern.CASE_INSENSITIVE), rule(ALREADY_CANCELLED, HIGH)),
            new KeywordRule(Pattern.compile(
                    "timed? ?out|connection (refused|reset)|i/o error|service unavailable|bad gateway|\\b50[234]\\b",
                    Pattern.CASE_INSENSITIVE), rule(PG_TEMPORARY_OUTAGE, HIGH)),
            new KeywordRule(Pattern.compile("bad request|\\b400\\b|\\b422\\b", Pattern.CASE_INSENSITIVE), rule(INVALID_REQUEST, HIGH))
    );

    public FailureClassification classify(FailureContext context) {
        String error = context.lastError();
        if (error == null || error.isBlank()) {
            return rule(UNKNOWN, LOW);
        }

        String trimmed = error.strip();

        FailureClassification byErrorCode = BY_ERROR_CODE_MESSAGE.get(trimmed);
        if (byErrorCode != null) {
            return byErrorCode;
        }

        FailureClassification byReason = BY_PROCESSOR_REASON.get(trimmed);
        if (byReason != null) {
            return byReason;
        }

        return KEYWORD_RULES.stream()
                .filter(keywordRule -> keywordRule.pattern().matcher(trimmed).find())
                .map(KeywordRule::classification)
                .findFirst()
                .orElseGet(() -> rule(UNKNOWN, LOW));
    }

    private static FailureClassification rule(FailureCategory category, Confidence confidence) {
        return FailureClassification.byRule(category, confidence);
    }

    private record KeywordRule(Pattern pattern, FailureClassification classification) {
    }
}
