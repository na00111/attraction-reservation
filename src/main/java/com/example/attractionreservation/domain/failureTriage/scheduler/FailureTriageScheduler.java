package com.example.attractionreservation.domain.failureTriage.scheduler;

import com.example.attractionreservation.domain.failureTriage.facade.FailureTriageProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 재시도 스케줄러들이 포기(DEAD)한 실패를 주기적으로 분류해 알린다.
 *
 * <p>기존 재시도 스케줄러 "안"이 아니라 "옆"에 둔 이유: 재시도 코드에 분류 호출을 넣으면 Jev 응답 시간이 재시도 주기를 늦추고,
 * 분류 오류가 재시도 흐름에 섞인다. 별도 스케줄러는 DEAD 행을 읽기만 하므로 꺼도(enabled=false) 결제·환불 동작은 그대로다.
 *
 * <p>DEAD는 이미 재시도를 마친 상태라 급하게 돌 필요가 없어 주기를 재시도 스케줄러(30초)보다 길게 둔다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.failure-triage.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class FailureTriageScheduler {
    private final FailureTriageProcessor failureTriageProcessor;
    private final int batchSize;

    public FailureTriageScheduler(
            FailureTriageProcessor failureTriageProcessor,
            // 종류(환불·보상·웹훅)별 한 주기 최대 처리 건수. Jev를 쓰면 건마다 LLM을 부를 수 있으므로 작게 둔다.
            @Value("${payment.failure-triage.scheduler.batch-size:10}") int batchSize
    ) {
        this.failureTriageProcessor = failureTriageProcessor;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${payment.failure-triage.scheduler.fixed-delay:1m}")
    public void triageDeadFailures() {
        try {
            failureTriageProcessor.triagePending(batchSize);
        } catch (RuntimeException e) {
            // 대상 조회 실패 등. 다음 주기에 다시 시도한다.
            log.error("[FailureTriage] 스케줄러 실행 실패", e);
        }
    }
}
