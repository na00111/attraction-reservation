package com.example.attractionreservation.domain.failureTriage.port;

import com.example.attractionreservation.domain.failureTriage.dto.FailureContext;
import com.example.attractionreservation.domain.failureTriage.port.dto.JevVerdict;

import java.util.Optional;

/**
 * Jev(LLM 판단기)에게 실패 원인을 묻는 포트. PaymentGateway처럼 도메인은 인터페이스만 알고 구현은 infra에 둔다.
 * LLM 제공자·프롬프트·API 키가 바뀌어도 분류 흐름과 테스트는 바뀌지 않게 하기 위해서다.
 *
 * <p>구현체는 다음을 보장해야 한다.
 * <ul>
 *     <li>예외를 던지지 않는다. 타임아웃·응답 파싱 실패·선택지 밖의 답은 모두 빈 값으로 돌려준다.
 *     Jev가 죽어도 알림은 원문으로 나가야 하기 때문이다.</li>
 *     <li>호출에 시간 제한을 건다. 스케줄러 한 주기가 LLM 응답 때문에 멈추지 않게 한다.</li>
 *     <li>선택지는 {@code FailureCategory} 네 개로 닫아서 묻는다.</li>
 * </ul>
 *
 * @return Jev의 답. 꺼져 있거나 답하지 못했으면 빈 값
 */
public interface JevClient {
    Optional<JevVerdict> classify(FailureContext context);
}
