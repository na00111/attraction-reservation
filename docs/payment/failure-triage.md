# 결제·환불 실패 원인 분류 (Failure Triage)

재시도 스케줄러가 포기한(DEAD) 결제·환불 실패의 원인을 분류하고, 분류 결과로 **운영자 알림의 우선순위**를 정한다.

> **지켜야 할 선**
> 분류 결과는 알림 우선순위에만 쓴다. Jev(LLM)의 판단으로 환불을 승인하거나 취소하지 않고, DEAD를 되살리거나 재시도를 예약하지도 않는다.
> 돈이 오가는 결정은 지금처럼 `RefundProcessor`·`PaymentCompensationProcessor`·`WebhookEventProcessor`의 결정적(deterministic) 코드가 한다.
> 이 기능은 원본 테이블을 **읽기만** 한다.

---

## 1. 왜 필요한가

환불이 5번 실패해 DEAD가 되면 지금은 `log.error("... 수동 확인 필요")` 한 줄이 남는다. 운영자는 모든 DEAD를 같은 무게로 받는다.
그런데 DEAD의 원인에 따라 해야 할 일이 다르다.

| 원인 | 운영자가 할 일 | 급한가 |
|---|---|---|
| PG 일시 장애 | PG가 복구되면 일괄 확인 | 기다려도 됨 |
| 요청 데이터 오류 | 금액·원장을 직접 확인하고 수동 처리 | 지금 봐야 함 |
| 이미 취소됨 | PG 기준으로 원장 대사 | 확인만 하면 됨 |
| 알 수 없음 | 원문을 읽고 판단 | 돈의 상태를 모르므로 지금 봐야 함 |

세 엔티티(`Refund`·`PaymentCompensation`·`WebhookEvent`)에 이미 `lastError`·`attemptCount`가 있어 새 도메인 데이터를 만들 필요가 없다.

## 2. 구조

```
                         (기존, 변경 없음)
RefundScheduler ─────────▶ RefundProcessor ─────────────┐
PaymentCompensationScheduler ▶ PaymentCompensationProcessor ┼─▶ status = DEAD, last_error 기록
WebhookEventScheduler ───▶ WebhookEventProcessor ───────┘
                                                         │ (읽기만)
                         (이번 PR)                        ▼
FailureTriageScheduler ─▶ FailureTriageProcessor
   (1분 주기)                 │
                              ├─ ① FailureTriageService.findUntriagedFailures   [readOnly 트랜잭션]
                              │      DEAD 이면서 failure_triages 에 기록이 없는 행
                              ├─ ② FailureClassifier.classify                    [트랜잭션 밖]
                              │      RuleBasedFailureClassifier → (확신 낮으면) JevClient
                              ├─ ③ FailureTriageService.saveTriage               [짧은 트랜잭션]
                              │      (source_type, source_id) UNIQUE 로 중복 방지
                              └─ ④ FailureAlertSender.send                       저장에 성공한 처리만
```

패키지 (`domain/failureTriage`)

| 위치 | 클래스 | 역할 |
|---|---|---|
| `scheduler` | `FailureTriageScheduler` | 주기 실행. `payment.failure-triage.scheduler.enabled=false`로 끌 수 있다 |
| `facade` | `FailureTriageProcessor` | 조회 → 분류 → 저장 → 알림 흐름. 트랜잭션 없음 |
| `service` | `FailureTriageService` | 대상 조회(readOnly), 분류 기록 저장 |
| `classifier` | `RuleBasedFailureClassifier` | 1단계: `lastError` 문자열 규칙 분류 |
| `classifier` | `FailureClassifier` | 규칙 → Jev → 원문 순서 결정 |
| `port` | `JevClient`, `FailureAlertSender` | LLM·알림 채널 포트. 구현은 `infra/` |
| `entity` | `FailureTriage` | 분류 기록(`failure_triages`). 중복 알림 방지 + 측정 데이터 |
| `infra/jev` | `DisabledJevClient` | 기본 구현. 항상 답하지 않음 |
| `infra/alert` | `LogFailureAlertSender` | 기본 구현. `log.warn`/`log.error` 한 줄 |

## 3. 분류 흐름과 확신도

```
규칙 HIGH ─────────────────────────────▶ 규칙 결과 사용          (classifiedBy = RULE)
규칙 LOW ─▶ Jev 확신도 ≥ 0.8 ──────────▶ Jev 결과 사용           (classifiedBy = JEV)
          └▶ Jev 확신도 낮음 / 무응답 / 오류 ─▶ 즉시 확인 + 원문   (classifiedBy = NONE)
                                               Jev 요약이 있으면 함께 보냄
```

- **확신도가 높으면** 분류에 정해진 우선순위로 알린다. 일시 장애면 "대기", 데이터 오류면 "즉시 확인".
- **확신도가 낮으면** 분류와 관계없이 "즉시 확인"으로 올리고 원문(`lastError`)을 붙인다. Jev 요약이 있으면 원문과 **함께** 보낸다(원문 대신이 아니다).
- 원문은 확신도와 관계없이 **항상** 알림에 들어간다. 분류는 요약일 뿐이라 운영자가 원문을 보고 분류가 틀렸음을 알아챌 수 있어야 한다.

### 분류 → 우선순위

| `FailureCategory` | 확신 HIGH | 확신 LOW |
|---|---|---|
| `PG_TEMPORARY_OUTAGE` PG 일시 장애 | `WAIT` 대기 (log.warn) | `CHECK_NOW` |
| `INVALID_REQUEST` 요청 데이터 오류 | `CHECK_NOW` 즉시 확인 | `CHECK_NOW` |
| `ALREADY_CANCELLED` 이미 취소됨 | `CHECK` 확인 | `CHECK_NOW` |
| `UNKNOWN` 알 수 없음 | `CHECK_NOW` | `CHECK_NOW` |

### 규칙 분류표 (`RuleBasedFailureClassifier`)

판단 순서는 ① ErrorCode 메시지 정확히 일치 → ② 처리 코드가 남긴 사유 문구 → ③ 예외 원문 키워드 → ④ 알 수 없음. 앞 단계일수록 문자열의 출처가 확실하다.

| `lastError` | 분류 | 확신 | 근거 |
|---|---|---|---|
| `PAYMENT_ALREADY_CANCELLED` 메시지 | 이미 취소됨 | HIGH | PortOne이 이미 취소됐다고 응답 |
| `PAYMENT_CANCELLABLE_AMOUNT_MISMATCH` 메시지 | 이미 취소됨 | HIGH | 앞선 취소가 PG에 반영돼 취소 가능 금액이 달라짐 |
| `PAYMENT_INVALID_AMOUNT` / `INCORRECT_AMOUNT` / `NOT_FOUND` / `PG_PAYMENT_NOT_FOUND` 메시지 | 데이터 오류 | HIGH | 다시 보내도 같은 거절 |
| `PAYMENT_GATEWAY_ERROR` 메시지 | 일시 장애 | **LOW** | 대부분 5xx·통신 실패지만 API 키 오류 같은 4xx도 같은 코드 |
| `PAYMENT_CANCELLATION_FAILED` 메시지 | 알 수 없음 | **LOW** | PG 4xx 거절과 통신 재시도 소진이 같은 코드 |
| `PG 취소 실패 확인` | 데이터 오류 | LOW | PG가 취소 실패로 확정. 원인은 모름 |
| `PG 취소 확인 기한 초과` | 알 수 없음 | LOW | 24시간 동안 PG가 확정하지 않음 |
| `원장 상태와 충돌: 수동 확인 필요` 등 웹훅 DEAD 사유 | 데이터 오류 | HIGH | 결정적 코드가 이미 원장 불일치로 판단 |
| `lock wait` / `deadlock` / `jdbc` 포함 | 알 수 없음 | LOW | 우리 DB 문제. 아래 타임아웃 규칙에 걸리지 않게 먼저 본다 |
| `timed out` / `connection refused` / `502·503·504` 포함 | 일시 장애 | HIGH | 통신·PG 장애 |
| `bad request` / `400` / `422` 포함 | 데이터 오류 | HIGH | |
| 그 밖, 빈 값 | 알 수 없음 | LOW | Jev 또는 운영자에게 |

ErrorCode 메시지는 문구를 복사하지 않고 `ErrorCode.X.getMessage()`로 꺼내므로 메시지가 바뀌어도 규칙이 따라간다.
②의 사유 문구는 각 Processor의 private 상수라 문구로 맞췄다. 문구가 바뀌면 ④(알 수 없음, LOW)로 떨어질 뿐, **잘못 낮은 우선순위가 되지는 않는다.**

## 4. 설계 결정과 이유

| 결정 | 이유 | 버린 대안 |
|---|---|---|
| 규칙을 먼저, Jev는 규칙이 확신하지 못할 때만 | 현재 `lastError` 대부분이 ErrorCode 메시지라 값의 종류가 적고 고정돼 있다. 같은 입력에 같은 답을 내는 쪽이 테스트·설명이 쉽고 LLM 비용도 없다 | 모든 실패를 Jev에게 묻기 |
| 선택지를 닫힌 enum 4개로 | LLM이 자유 문장으로 답하면 우선순위로 결정적으로 바꿀 수 없다 | 자유 텍스트 분류 |
| 확신도를 HIGH/LOW 두 단계로 저장 | 쓰이는 곳이 "우선순위를 낮춰도 되는가" 하나뿐이고, LLM 점수는 보정되지 않은 값이라 세밀한 숫자는 의미가 없다 | 0~1 숫자 저장 |
| 확신이 낮으면 항상 즉시 확인 | 진짜 데이터 오류를 "대기"로 낮춰 놓치는 비용이 운영자가 한 번 더 보는 비용보다 크다 | 낮은 확신도 분류대로 |
| 재시도 스케줄러 **옆**에 별도 스케줄러 | 재시도 코드에 넣으면 Jev 응답 시간이 재시도 주기를 늦추고, 분류 오류가 돈 흐름에 섞인다. 별도 스케줄러는 꺼도 결제·환불 동작이 그대로다 | `RefundProcessor.logFailureStatus` 안에서 분류 호출 |
| 분류 기록을 별도 테이블 `failure_triages`로 | 원본 세 엔티티와 결제 코드를 건드리지 않는다. 원본과 FK도 맺지 않아 붙였다 뗄 수 있다 | 원본에 `alerted_at`·`category` 컬럼 추가 |
| (source_type, source_id) UNIQUE + 저장 후 알림 | 여러 인스턴스가 같은 행을 분류해도 저장에 성공한 한 곳만 알린다(중복 알림 없음) | 알림 후 저장(중복 가능) |
| Jev 호출을 트랜잭션 밖에서 | 외부 API를 기다리며 DB 커넥션을 잡지 않는다. `RefundProcessor`의 "점유 → PG 호출 → 결과 기록"과 같은 이유 | 한 트랜잭션에서 조회·분류·저장 |
| Jev·알림을 포트로 | `PaymentGateway`처럼 도메인은 인터페이스만 안다. LLM 제공자·알림 채널이 바뀌어도 흐름과 테스트가 그대로다 | 구현을 도메인에 직접 |
| 종류별 limit | 한 종류가 대량으로 DEAD가 돼도(PG 장애로 보상 취소가 몰림) 다른 종류의 알림이 밀리지 않는다 | 전체 합산 limit |

**트레이드오프: 알림은 최대 한 번(at-most-once).** 기록을 저장한 뒤 알림 전송이 실패하면 그 건은 다시 보내지 않는다.
지금 알림은 로그라 실패할 일이 거의 없고, 분류 기록은 남아 `failure_triages`에서 찾을 수 있다. Slack 등 외부 알림으로 바꾸면 `alerted_at` 컬럼으로 재전송을 검토한다.

## 5. 구현하며 확인한 한계

### 5-1. `lastError`에는 PG 응답 원문이 없다

처음 가정은 "`lastError`에 PG 응답 문자열이 남아 있다"였지만, 실제로는 대부분 **ErrorCode 메시지**다.
`PortOneClient`는 PG의 `httpStatus`·`responseBody`를 `log.warn`으로만 남기고, 던지는 예외는 `BusinessException(ErrorCode)`라
Processor가 저장하는 `e.getMessage()`는 "결제 취소에 실패했습니다." 같은 고정 문구가 된다.

그 결과 가장 흔할 DEAD 사유인 `PAYMENT_CANCELLATION_FAILED`는 **PG 4xx 거절(데이터 오류)과 통신 재시도 소진(일시 장애)이 같은 문자열**이다.
규칙은 이것을 구분할 수 없어 LOW로 두었고, Jev도 같은 문자열만 보므로 구분하기 어렵다. 지금은 이 건들이 "즉시 확인 + 원문"으로 나간다(안전한 쪽).

**제안(별도 PR, 결제 담당과 협의 필요):** `PortOneClient`가 실패 원인을 `lastError`에 남기게 한다.
예: `"결제 취소에 실패했습니다. [http=400, type=INVALID_REQUEST]"`. 그러면 규칙만으로 대부분 HIGH가 되고 Jev가 필요한 건이 줄어든다.
이번 PR은 결제 코드를 건드리지 않는 범위로 한정했다.

### 5-2. 실제 데이터로 측정하기 어렵다

개발 단계에서는 DEAD 건수가 적다. 그래서 테스트에 `FailureErrorSamples`(테스트용 에러 샘플)를 두고 정답지로 쓴다.
코드가 실제로 남기는 문자열(ErrorCode 메시지, Processor 사유)과 감싸지지 않은 예외 원문 예시를 모았다.

샘플을 만들면서 규칙 오분류를 하나 찾아 고쳤다. `"Lock wait timeout exceeded"`(DB 락)가 `timeout` 키워드에 걸려 "PG 일시 장애·HIGH → 대기"가 됐다.
우리 DB 문제를 PG 장애로 낮추는 것이라, DB 오류 규칙을 타임아웃 규칙보다 먼저 두어 "알 수 없음·LOW"로 바꿨다.

운영에서 측정할 방법: `failure_triages`에 `raw_error`·`category`·`classified_by`가 남는다. 운영자가 실제 원인을 확인한 뒤
라벨을 붙이면(후속 작업) 규칙과 Jev의 정확도를 따로 잴 수 있고, 그 결과로 Jev 임계값(`min-confidence`)을 조정한다.

### 5-3. Jev는 아직 연결되지 않았다

`JevClient` 포트와 분류 흐름, 임계값 처리, 실패 시 대체 경로까지는 구현·테스트했다. 실제 LLM 구현체는 없고 기본 구현(`DisabledJevClient`)은 항상 답하지 않는다.
그래서 지금 동작은 "규칙 HIGH면 분류대로, 나머지는 즉시 확인 + 원문"이다. Jev 없이도 알림이 빠지지 않게 하는 것이 목표라 이 상태로도 쓸 수 있다.

## 6. Jev 구현체를 붙이는 방법

1. `infra/jev`에 `JevClient` 구현체를 만들고 `@ConditionalOnProperty(name = "payment.failure-triage.jev.enabled", havingValue = "true")`를 붙인다.
   `DisabledJevClient`는 같은 설정이 `false`(기본)일 때만 등록되므로 둘이 동시에 뜨지 않는다.
2. 포트 계약을 지킨다.
   - 예외를 던지지 않는다. 타임아웃·파싱 실패·선택지 밖의 답은 `Optional.empty()`.
   - 호출에 시간 제한을 건다(예: 5초). 스케줄러 한 주기가 LLM 응답 때문에 멈추지 않게 한다.
   - 선택지는 `FailureCategory` 네 개로 닫아서 묻고, JSON으로만 답하게 한다.
3. Jev에게 보내는 값은 `FailureContext`(종류, ID, pgId, 시도 횟수, lastError)뿐이다. 개인정보는 세 엔티티에 없으므로 넘어가지 않는다.

프롬프트 예시:

```
결제·환불 PG 취소가 {attemptCount}번 실패해 재시도를 멈췄다. 마지막 실패 사유로 원인을 하나 고르라.
- PG_TEMPORARY_OUTAGE: PG 일시 장애(타임아웃, 5xx, 통신 실패)
- INVALID_REQUEST: 요청 데이터 오류(금액 불일치, 없는 결제 등 다시 보내도 같은 거절)
- ALREADY_CANCELLED: 이미 취소됨
- UNKNOWN: 알 수 없음
사유: "{lastError}"
JSON으로만 답하라: {"category": "...", "confidence": 0.0~1.0, "summary": "운영자용 한 줄 요약"}
```

## 7. 설정

| 키 | 기본값 | 설명 |
|---|---|---|
| `payment.failure-triage.scheduler.enabled` | `true` | `false`면 스케줄러를 등록하지 않는다. 결제·환불 동작에는 영향 없음 |
| `payment.failure-triage.scheduler.fixed-delay` | `1m` | 이전 실행이 끝난 뒤 다음 실행까지 간격 |
| `payment.failure-triage.scheduler.batch-size` | `10` | 종류(환불·보상·웹훅)별 한 주기 최대 처리 건수 |
| `payment.failure-triage.jev.enabled` | `false` | `true`면 실제 Jev 구현체를 쓴다(구현체 필요) |
| `payment.failure-triage.jev.min-confidence` | `0.8` | Jev 답을 믿고 우선순위를 정할 최소 확신도. 측정 전이라 보수적으로 높게 둠 |

## 8. 알림 예시 (`LogFailureAlertSender`)

```
WARN  [FailureTriage] [대기] 결제·환불 실패 분류: sourceType=REFUND, sourceId=12, pgId=..., attemptCount=5,
      category=PG_TEMPORARY_OUTAGE, confidence=HIGH, classifiedBy=RULE, summary=null, lastError=Read timed out
ERROR [FailureTriage] [즉시 확인] 결제·환불 실패 분류: sourceType=PAYMENT_COMPENSATION, sourceId=3, pgId=..., attemptCount=5,
      category=UNKNOWN, confidence=LOW, classifiedBy=NONE, summary=null, lastError=결제 취소에 실패했습니다.
```

기존 "수동 확인 필요" `log.error`는 그대로 남는다. 이 알림은 그 옆에 원인과 우선순위를 더하는 것이다.

## 9. 테스트

| 테스트 | 확인하는 것 |
|---|---|
| `RuleBasedFailureClassifierTest` | `FailureErrorSamples` 18개 + 빈 값·공백 처리 |
| `FailureClassifierTest` | 규칙 HIGH면 Jev 미호출 / Jev 임계값 이상·같음·미만 / 무응답 / 예외 / 분류 없는 답 |
| `FailureClassificationTest` | 분류 × 확신도 → 우선순위 표 |
| `FailureTriageProcessorTest` | 저장 성공만 알림 / 중복·UNIQUE 충돌 시 알림 없음 / 한 건 실패해도 계속 / 분류 후 저장 순서 |
| `FailureTriageSchedulerTest` | 배치 크기 전달, 예외 삼킴, `enabled` 설정 |
| `FailureTriageRepositoryTest` | DEAD만 조회 / 분류된 행 제외 / 종류별 limit / 중복 저장 방지 / UNIQUE / 500자 자르기 |

## 10. 후속 작업

- `PortOneClient`가 `lastError`에 HTTP 상태·PG 오류 타입을 남기기 (5-1, 결제 담당과 협의)
- 실제 Jev 구현체와 타임아웃 설정 (6)
- 운영자가 실제 원인을 라벨링하는 방법과 정확도 측정 (5-2)
- 외부 알림 채널(Slack 등)과 `alerted_at` 재전송 (4의 트레이드오프)
