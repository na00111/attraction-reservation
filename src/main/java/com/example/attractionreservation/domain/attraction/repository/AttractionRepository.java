package com.example.attractionreservation.domain.attraction.repository;

import com.example.attractionreservation.domain.attraction.entity.Attraction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// JpaRepository: 기본 CRUD + 이름으로 만드는 쿼리(Derived Query)
// AttractionRepositoryCustom: QueryDSL로 직접 만든 복잡한 쿼리
// → 서비스에서는 attractionRepository 하나로 둘 다 사용할 수 있다.
public interface AttractionRepository extends JpaRepository<Attraction, Long>, AttractionRepositoryCustom {

    // 등록 시 이름 중복 확인
    boolean existsByNameAndDeletedAtIsNull(String name);

    // 삭제되지 않은 어트랙션만 단건 조회
    Optional<Attraction> findByIdAndDeletedAtIsNull(Long id);

    // 수정 시 이름 중복 확인 (자기 자신은 제외)
    boolean existsByNameAndIdNotAndDeletedAtIsNull(String name, Long id);

    // ★ 추가: 슬롯 등록·시간 수정 시 같은 어트랙션의 겹침 검사를 한 명씩 하도록 어트랙션 행을 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Attraction a WHERE a.id = :attractionId AND a.deletedAt IS NULL")
    Optional<Attraction> findActiveByIdForUpdate(@Param("attractionId") Long attractionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Attraction a WHERE a.id = :attractionId")
    Optional<Attraction> findByIdForUpdate(@Param("attractionId") Long attractionId);

    //Q&A 분류 대상 조회 선택지 순서가 jev 결과에 영향을 줄 수 있어 id 순으로 고정한다. -> 위치 편향 영향을 안받기 위해
    List<Attraction> findAllByDeletedAtIsNullOrderByIdAsc();

    //ORDER BY가 없으면 DB는 순서를 안정한다.
    //그리고 AI는 보기 순서에 영향을 받는 경향이 있어요(위치 편향, position bias). 예를 들어 앞쪽 보기를 더 고르는 식이에요.
    // 둘이 겹치면 같은 질문인데 요청마다 답이 달라지고, 버그를 재현하기 어려워져요.
    //주의: 순서 고정은 편향을 없애는 게 아니라 일정하게 만드는 거예요.
    //-> 편향을 없앤다는 것 (X):
    //순서를 고정한다고 해서 AI의 "앞쪽에 있는 걸 더 선호하는 성향(위치 편향)" 자체가 사라지는 것은 아니에요. 모델의 본질적인 성향은 그대로 남아 있습니다.
    //
    //편향을 일정하게 만든다는 것 (O):
    //데이터를 항상 id ASC (1번, 2번, 3번 순서)로 똑같이 주면, AI가 편향을 가지더라도 '매번 똑같은 조건'에서 편향을 겪게 됩니다.
    // AI 자체의 무작위성은 남아 있어요. 위 예시의 숫자는 이해를 돕기 위해 지어낸 값이에요.
}
