package com.example.attractionreservation.domain.failureTriage.scheduler;

import com.example.attractionreservation.domain.failureTriage.facade.FailureTriageProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FailureTriageSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(FailureTriageProcessor.class, () -> mock(FailureTriageProcessor.class))
            .withUserConfiguration(FailureTriageScheduler.class);

    @Test
    @DisplayName("스케줄러가 실행되면 설정한 배치 크기로 분류를 요청한다.")
    void triageDeadFailures_scheduled_TriageWithBatchSize() {
        //given
        FailureTriageProcessor processor = mock(FailureTriageProcessor.class);
        FailureTriageScheduler scheduler = new FailureTriageScheduler(processor, 10);

        //when
        scheduler.triageDeadFailures();

        //then
        verify(processor).triagePending(10);
    }

    @Test
    @DisplayName("처리 중 예외가 나도 스케줄러 밖으로 던지지 않는다.")
    void triageDeadFailures_error_Swallowed() {
        //given
        FailureTriageProcessor processor = mock(FailureTriageProcessor.class);
        willThrow(new DataAccessResourceFailureException("db down")).given(processor).triagePending(10);
        FailureTriageScheduler scheduler = new FailureTriageScheduler(processor, 10);

        //when & then
        assertThatCode(scheduler::triageDeadFailures).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("설정이 없으면 스케줄러를 등록한다.")
    void scheduler_noProperty_Registered() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(FailureTriageScheduler.class));
    }

    @Test
    @DisplayName("enabled가 false면 스케줄러를 등록하지 않는다.")
    void scheduler_disabled_NotRegistered() {
        contextRunner
                .withPropertyValues("payment.failure-triage.scheduler.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FailureTriageScheduler.class));
    }
}
