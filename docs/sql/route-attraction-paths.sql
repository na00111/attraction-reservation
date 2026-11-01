-- 추천 경로용 어트랙션 간 이동 거리/시간 테이블 + 에버랜드 일부 구간 시드 데이터
-- 목적: "정문 기준 고정 이동 시간"(entrance_move_minutes) 대신
--       "직전 어트랙션 → 다음 어트랙션" 이동 시간으로 경로를 계산하기 위한 데이터
-- 한 행 = 출발 어트랙션 → 도착 어트랙션의 네이버 지도 도보 길찾기 결과 (방향별로 한 행씩 저장)
-- 현재는 대상 어트랙션의 모든 쌍을 실측했으므로, 조회한 값을 그대로 이동 시간으로 사용한다.
--   (어트랙션이 늘어 모든 쌍을 실측하기 어려워지면, 인접 구간만 저장하고 최단 경로 알고리즘으로 계산하는 방식으로 전환)
-- MySQL DDL은 일반 트랜잭션 롤백으로 되돌릴 수 없다. 테이블이 없는 스키마에 한 번만 적용한다.

-- 1. 테이블 생성
CREATE TABLE attraction_paths (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    from_attraction_id  BIGINT       NOT NULL,                 -- 출발 어트랙션
    to_attraction_id    BIGINT       NOT NULL,                 -- 도착 어트랙션
    distance_m          INT          NOT NULL,                 -- 도보 이동 거리 (m, 네이버 지도 기준)
    move_minutes        INT          NOT NULL,                 -- 도보 이동 시간 (분, 네이버 지도 기준)
    step_count          INT          NULL,                     -- 걸음 수 (네이버 지도 기준, 참고용)
    is_estimated        BOOLEAN      NOT NULL DEFAULT FALSE,   -- TRUE = 실측이 아닌 근사값 (하버사인 × 우회계수 등)
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,    -- FALSE = 공사 등으로 일시 폐쇄
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_attraction_paths_from FOREIGN KEY (from_attraction_id) REFERENCES attractions (id),
    CONSTRAINT fk_attraction_paths_to   FOREIGN KEY (to_attraction_id)   REFERENCES attractions (id),
    -- 같은 방향의 구간 중복 저장 방지. 복합 인덱스의 맨 앞이 from_attraction_id라
    -- "이 어트랙션에서 출발하는 구간" 조회(WHERE from_attraction_id = ?)에도 그대로 쓰인다.
    CONSTRAINT uk_attraction_paths_from_to UNIQUE (from_attraction_id, to_attraction_id),
    CONSTRAINT ck_attraction_paths_not_self CHECK (from_attraction_id <> to_attraction_id),
    CONSTRAINT ck_attraction_paths_distance CHECK (distance_m > 0),
    CONSTRAINT ck_attraction_paths_minutes  CHECK (move_minutes > 0)
);

-- 2. 어트랙션 id 조회
--    attractions.name이 실제 DB 값과 다르면 변수가 NULL이 되고,
--    아래 INSERT가 NOT NULL 제약으로 실패한다. (잘못된 id로 조용히 들어가는 것을 막음)
--    실행 전에 이름을 실제 DB 값에 맞게 확인할 것.
SET @bumper_car    := (SELECT id FROM attractions WHERE name = '범퍼카'            AND deleted_at IS NULL);
SET @space_fighter := (SELECT id FROM attractions WHERE name = '우주전투기'        AND deleted_at IS NULL);
SET @amazon        := (SELECT id FROM attractions WHERE name = '아마존 익스프레스' AND deleted_at IS NULL);
SET @monimo        := (SELECT id FROM attractions WHERE name = '모니모 러쉬'       AND deleted_at IS NULL);
SET @thunder_falls := (SELECT id FROM attractions WHERE name = '썬더폴스'          AND deleted_at IS NULL);

-- 3. 시드 데이터: 네이버 지도 도보 길찾기 측정값 20건 (5개 어트랙션 × 출발지별 4개 도착지, 값 수정 없음)

-- 범퍼카 출발
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@bumper_car, @amazon,        641, 11, 1001);
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@bumper_car, @monimo,        632, 11,  988);
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@bumper_car, @thunder_falls, 131,  3,  220);
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@bumper_car, @space_fighter, 322,  5,  504);

-- 우주전투기 출발
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@space_fighter, @bumper_car,    322,  5,  504);
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@space_fighter, @amazon,        433, 10,  676);
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@space_fighter, @monimo,        424,  9,  662);
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@space_fighter, @thunder_falls, 452,  8,  705);

-- 썬더폴스 출발
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@thunder_falls, @bumper_car,    131,  3,  220);
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@thunder_falls, @amazon,        808, 14, 1263);
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@thunder_falls, @monimo,        799, 14, 1249);
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@thunder_falls, @space_fighter, 452,  8,  705);

-- 아마존 익스프레스 출발
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@amazon, @monimo,        217,  3,  339);
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@amazon, @thunder_falls, 808, 16, 1263);
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@amazon, @bumper_car,    641, 12, 1001);
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@amazon, @space_fighter, 409,  8,  676);

-- 모니모 러쉬 출발
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@monimo, @amazon,        217,  3,  339);
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@monimo, @thunder_falls, 799, 16, 1249);
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@monimo, @bumper_car,    632, 12,  988);
INSERT INTO attraction_paths (from_attraction_id, to_attraction_id, distance_m, move_minutes, step_count) VALUES (@monimo, @space_fighter, 424, 10,  622);

-- 4. 적용 확인: 20행이 나오고, 모든 행의 from_name / to_name이 NULL이 아니면 성공이다.
SELECT p.id,
       f.name AS from_name,
       t.name AS to_name,
       p.distance_m,
       p.move_minutes,
       p.step_count
FROM attraction_paths p
JOIN attractions f ON f.id = p.from_attraction_id
JOIN attractions t ON t.id = p.to_attraction_id
ORDER BY f.name, t.name;

-- 5. 누락 검사: 대상 어트랙션 5개 기준으로 빠진 (출발, 도착) 쌍이 있으면 출력된다. 결과가 0행이어야 한다.
SELECT f.name AS from_name, t.name AS to_name
FROM attractions f
JOIN attractions t ON t.id <> f.id
LEFT JOIN attraction_paths p
       ON p.from_attraction_id = f.id
      AND p.to_attraction_id   = t.id
WHERE f.id IN (@bumper_car, @space_fighter, @amazon, @monimo, @thunder_falls)
  AND t.id IN (@bumper_car, @space_fighter, @amazon, @monimo, @thunder_falls)
  AND p.id IS NULL;
