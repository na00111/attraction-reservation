package com.example.attractionreservation.domain.failureTriage.port;

import com.example.attractionreservation.domain.failureTriage.dto.FailureAlert;

/**
 * 운영자 알림 포트. 지금은 기존 결제 코드처럼 log.error 한 줄로 알린다(LogFailureAlertSender).
 * Slack·이메일이 정해지면 구현체만 바꾼다.
 */
public interface FailureAlertSender {
    void send(FailureAlert alert);
}
