package com.example.attractionreservation.domain.failureTriage.repository;

import com.example.attractionreservation.domain.failureTriage.entity.FailureTriage;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureSourceType;
import com.example.attractionreservation.domain.payment.entity.PaymentCompensation;
import com.example.attractionreservation.domain.payment.entity.WebhookEvent;
import com.example.attractionreservation.domain.payment.entity.enums.CompensationStatus;
import com.example.attractionreservation.domain.payment.entity.enums.WebhookStatus;
import com.example.attractionreservation.domain.refund.entity.Refund;
import com.example.attractionreservation.domain.refund.entity.enums.RefundStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 분류 기록 저장과 "아직 분류하지 않은 DEAD 행" 조회.
 *
 * <p>조회를 원본 세 Repository가 아닌 여기에 둔 이유: 조건이 "분류 기록이 없는"이라 failure_triages를 알아야 하고,
 * 결제·환불 Repository에 이 기능의 쿼리를 섞지 않기 위해서다. 원본 테이블은 읽기만 한다.
 *
 * <p>DEAD 상태 값은 파라미터로 받는다. 상태 전이는 원본 도메인이 정하므로, 어떤 값을 "포기된 실패"로 볼지는 호출하는 쪽이 원본 enum으로 명시한다.
 */
public interface FailureTriageRepository extends JpaRepository<FailureTriage, Long> {

    boolean existsBySourceTypeAndSourceId(FailureSourceType sourceType, Long sourceId);

    // FailureContext가 pgId를 쓰므로 결제를 함께 읽어 N+1을 막는다.
    @Query("""
            select r from Refund r
            join fetch r.payment
            where r.status = :status
              and not exists (select t.id from FailureTriage t where t.sourceType = :sourceType and t.sourceId = r.id)
            order by r.id
            """)
    List<Refund> findUntriagedRefunds(@Param("status") RefundStatus status,
                                      @Param("sourceType") FailureSourceType sourceType,
                                      Pageable pageable);

    @Query("""
            select c from PaymentCompensation c
            where c.status = :status
              and not exists (select t.id from FailureTriage t where t.sourceType = :sourceType and t.sourceId = c.id)
            order by c.id
            """)
    List<PaymentCompensation> findUntriagedCompensations(@Param("status") CompensationStatus status,
                                                         @Param("sourceType") FailureSourceType sourceType,
                                                         Pageable pageable);

    @Query("""
            select w from WebhookEvent w
            where w.status = :status
              and not exists (select t.id from FailureTriage t where t.sourceType = :sourceType and t.sourceId = w.id)
            order by w.id
            """)
    List<WebhookEvent> findUntriagedWebhookEvents(@Param("status") WebhookStatus status,
                                                  @Param("sourceType") FailureSourceType sourceType,
                                                  Pageable pageable);
}
