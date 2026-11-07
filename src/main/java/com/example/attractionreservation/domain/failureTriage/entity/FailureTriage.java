package com.example.attractionreservation.domain.failureTriage.entity;

import com.example.attractionreservation.domain.failureTriage.dto.FailureClassification;
import com.example.attractionreservation.domain.failureTriage.dto.FailureContext;
import com.example.attractionreservation.domain.failureTriage.entity.enums.AlertPriority;
import com.example.attractionreservation.domain.failureTriage.entity.enums.ClassifiedBy;
import com.example.attractionreservation.domain.failureTriage.entity.enums.Confidence;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureCategory;
import com.example.attractionreservation.domain.failureTriage.entity.enums.FailureSourceType;
import com.example.attractionreservation.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DEAD가 된 실패 한 건의 분류 기록. 두 가지 역할을 한다.
 * <ol>
 *     <li>중복 알림 방지: (source_type, source_id) UNIQUE라 여러 인스턴스가 같은 행을 분류해도 한 곳만 저장에 성공하고,
 *     저장에 성공한 처리만 알림을 보낸다.</li>
 *     <li>측정 데이터: 규칙과 Jev가 각각 무엇으로 분류했는지 남겨, 나중에 실제 원인과 비교해 정확도를 잴 수 있게 한다.</li>
 * </ol>
 *
 * <p>원본 세 엔티티(Refund·PaymentCompensation·WebhookEvent)에 컬럼을 추가하지 않고 별도 테이블로 둔 이유:
 * 분류는 돈의 상태 전이와 무관한 부가 기능이므로, 결제 코드와 마이그레이션을 건드리지 않고 붙였다 뗄 수 있어야 한다.
 * 같은 이유로 원본과 FK·연관관계를 맺지 않는다.
 */
@Entity
@Table(name = "failure_triages",
        uniqueConstraints = @UniqueConstraint(name = "uk_failure_triages_source", columnNames = {"source_type", "source_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FailureTriage extends BaseTimeEntity {
    private static final int MAX_TEXT_LENGTH = 500; // 원본 last_error 컬럼 길이와 같다

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FailureSourceType sourceType;

    @Column(nullable = false)
    private Long sourceId;

    @Column(length = 40)
    private String pgId;

    @Column(nullable = false)
    private int attemptCount; // 분류 시점의 시도 횟수

    @Column(length = MAX_TEXT_LENGTH)
    private String rawError; // 분류 시점의 last_error. 원본이 나중에 바뀌어도 무엇을 보고 분류했는지 남긴다

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FailureCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Confidence confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ClassifiedBy classifiedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertPriority priority;

    @Column(length = MAX_TEXT_LENGTH)
    private String summary; // Jev 요약. 없으면 null

    private FailureTriage(FailureContext context, FailureClassification classification) {
        this.sourceType = context.sourceType();
        this.sourceId = context.sourceId();
        this.pgId = context.pgId();
        this.attemptCount = context.attemptCount();
        this.rawError = truncate(context.lastError());
        this.category = classification.category();
        this.confidence = classification.confidence();
        this.classifiedBy = classification.classifiedBy();
        this.priority = classification.priority();
        this.summary = truncate(classification.summary());
    }

    public static FailureTriage record(FailureContext context, FailureClassification classification) {
        return new FailureTriage(context, classification);
    }

    private String truncate(String text) {
        if (text == null || text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }

        return text.substring(0, MAX_TEXT_LENGTH);
    }
}
