package com.example.attractionreservation.domain.attraction.entity;

import com.example.attractionreservation.domain.attraction.entity.enums.OperationStatus;
import com.example.attractionreservation.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Entity
@Table(name = "attractions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attraction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "location", nullable = false, length = 50)
    private String location;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING) // // Enum 값을 숫자가 아닌 문자열로 데이터베이스에 저장
    @Column(name = "operation_status", nullable = false, length = 50)
    private OperationStatus operationStatus;

    @Column(name = "deleted_at")
    private Instant deletedAt; // 어트랙션이 삭제 처리된 시각을 저장


    // Attraction Service 등록에 필요
    private Attraction(String name, String description, String location, Integer durationMinutes) {
        this.name = name;
        this.description = description;
        this.location = location;
        this.durationMinutes = durationMinutes;
        this.operationStatus = OperationStatus.OPERATING; // 등록 시 기본값
    }

    public static Attraction create(String name, String description, String location,
                                    Integer durationMinutes, Integer entranceMoveMinutes) {
        Attraction attraction = new Attraction();
        attraction.name = name;
        attraction.description = description;
        attraction.location = location;
        attraction.durationMinutes = durationMinutes;
        attraction.entranceMoveMinutes = entranceMoveMinutes;
        attraction.operationStatus = OperationStatus.OPERATING;
        return attraction;
    }

    // Attraction Service 목록조회에 필요
    public boolean isOperating() {
        return this.operationStatus == OperationStatus.OPERATING;
    }

    // Attraction Service 수정에 필요
    public void update(String name, String description, String location,
                       Integer durationMinutes, Integer entranceMoveMinutes) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (location != null) this.location = location;
        if (durationMinutes != null) this.durationMinutes = durationMinutes;
        if (entranceMoveMinutes != null) this.entranceMoveMinutes = entranceMoveMinutes;
    }

    // Attraction Service 운영 상태 변경에 필요
    public void changeOperationStatus(OperationStatus operationStatus) {
        this.operationStatus = operationStatus;
    }

    // Attraction Service 삭제에 필요
    public void deleted() {
        this.operationStatus = OperationStatus.DELETED;
        this.deletedAt = Instant.now();
    }

    // 추가: 정문 → 이 어트랙션까지 예상 이동 시간(분)
    @Column(name = "entrance_move_minutes", nullable = false)
    private Integer entranceMoveMinutes;

    //추가: 어트랙션 Q&A 답변에 쓰는 탑승 정보, 관리자 api가 아직 없어 마이그레이션 sql로만 값을 넣는다.
    //의사결정 -> 전부 NULL을 허용한다. NULL은 제한 없음이 아니라 등록된 정보 없음이다.
    @Column(name = "min_height_cm")
    private Integer minHeightCm; //혼자 탈 수 있는 최소 키

    @Column(name = "max_height_cm")
    private Integer maxHeightCm; //탈 수 있는 최대 키

    @Column(name = "guardian_height_cm")
    private Integer guardianHeightCm;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;
}