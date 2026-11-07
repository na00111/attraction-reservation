package com.example.attractionreservation.domain.failureTriage.service;

import com.example.attractionreservation.domain.failureTriage.dto.FailureClassification;
import com.example.attractionreservation.domain.failureTriage.dto.FailureContext;
import com.example.attractionreservation.domain.failureTriage.entity.FailureTriage;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureSourceType;
import com.example.attractionreservation.domain.failureTriage.repository.FailureTriageRepository;
import com.example.attractionreservation.domain.payment.entity.enums.CompensationStatus;
import com.example.attractionreservation.domain.payment.entity.enums.WebhookStatus;
import com.example.attractionreservation.domain.refund.entity.enums.RefundStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 분류 대상 조회와 분류 기록 저장. 둘 다 짧은 트랜잭션이고, 그 사이의 분류(Jev 호출)는 트랜잭션 밖에서 한다.
 */
@Service
@RequiredArgsConstructor
public class FailureTriageService {
    private final FailureTriageRepository failureTriageRepository;

    /**
     * 아직 분류하지 않은 DEAD 행을 종류별로 최대 limit건씩 읽어 값만 꺼낸다.
     * 종류별로 따로 자르는 이유: 한 종류가 대량으로 DEAD가 돼도(예: PG 장애로 보상 취소가 몰림) 다른 종류의 알림이 밀리지 않게 한다.
     */
    @Transactional(readOnly = true)
    public List<FailureContext> findUntriagedFailures(int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<FailureContext> failures = new ArrayList<>();

        failures.addAll(failureTriageRepository
                .findUntriagedRefunds(RefundStatus.DEAD, FailureSourceType.REFUND, page)
                .stream().map(FailureContext::from).toList());
        failures.addAll(failureTriageRepository
                .findUntriagedCompensations(CompensationStatus.DEAD, FailureSourceType.PAYMENT_COMPENSATION, page)
                .stream().map(FailureContext::from).toList());
        failures.addAll(failureTriageRepository
                .findUntriagedWebhookEvents(WebhookStatus.DEAD, FailureSourceType.WEBHOOK_EVENT, page)
                .stream().map(FailureContext::from).toList());

        return failures;
    }

    /**
     * 분류 기록을 저장한다. 이미 다른 처리가 저장했으면 저장하지 않는다.
     * 먼저 존재 여부를 보고, 그 사이 다른 인스턴스가 끼어들면 UNIQUE 제약이 막는다(DataIntegrityViolationException).
     * 예외는 호출하는 Processor가 "다른 처리가 먼저 저장함"으로 받는다.
     *
     * @return 이번 호출이 저장했으면 true. 이 처리만 알림을 보낸다
     */
    @Transactional
    public boolean saveTriage(FailureContext context, FailureClassification classification) {
        if (failureTriageRepository.existsBySourceTypeAndSourceId(context.sourceType(), context.sourceId())) {
            return false;
        }

        failureTriageRepository.saveAndFlush(FailureTriage.record(context, classification));
        return true;
    }
}
