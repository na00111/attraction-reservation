package com.example.attractionreservation.infra.alert;

import com.example.attractionreservation.domain.failureTriage.dto.FailureAlert;
import com.example.attractionreservation.domain.failureTriage.entity.enums.AlertPriority;
import com.example.attractionreservation.domain.failureTriage.port.FailureAlertSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 로그로 보내는 운영자 알림. RefundProcessor의 "수동 확인 필요" log.error와 같은 방식이다.
 * 로그 수집기에서 "[FailureTriage]"와 priority로 필터링해 알림 채널에 연결하는 것을 전제로 한다.
 *
 * <p>대기(WAIT)는 warn, 나머지는 error로 남긴다. 레벨로도 우선순위를 걸러낼 수 있게 하기 위해서다.
 */
@Slf4j
@Component
public class LogFailureAlertSender implements FailureAlertSender {

    @Override
    public void send(FailureAlert alert) {
        String format = "[FailureTriage] [{}] 결제·환불 실패 분류: sourceType={}, sourceId={}, pgId={}, attemptCount={}, "
                + "category={}, confidence={}, classifiedBy={}, summary={}, lastError={}";
        Object[] args = {
                alert.priority().getLabel(),
                alert.context().sourceType(),
                alert.context().sourceId(),
                alert.context().pgId(),
                alert.context().attemptCount(),
                alert.classification().category(),
                alert.classification().confidence(),
                alert.classification().classifiedBy(),
                alert.classification().summary(),
                alert.context().lastError()
        };

        if (alert.priority() == AlertPriority.WAIT) {
            log.warn(format, args);
            return;
        }
        log.error(format, args);
    }
}
