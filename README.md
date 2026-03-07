# 이미지 처리 백엔드 서버

## 목차

1. [실행 방법](#실행-방법)
2. [API 명세](#api-명세)
3. [상태 모델 설계](#상태-모델-설계)
4. [외부 시스템 연동 방식](#외부-시스템-연동-방식)
5. [중복 요청 처리 전략](#중복-요청-처리-전략)
6. [실패 처리 전략](#실패-처리-전략)
7. [처리 보장 모델](#처리-보장-모델)
8. [서버 재시작 시 동작](#서버-재시작-시-동작)
9. [트래픽 증가 시 병목 지점](#트래픽-증가-시-병목-지점)

---

## 실행 방법

### 사전 준비

### 1. git repository를 clone 합니다
과제가 담겨져있는 git repository를 clone 합니다

### 2. Mock Worker API Key 발급
```bash
curl -X POST https://devrealteethai/mock/auth/issue-key \
  -H "Content-Type: application/json" \
  -d '{"candidateName": "김지협", "email": "hyeop@examplecom"}'
```
- Mock Worker와의 통신과 그 결과를 클라이언트에 뿌려주는게 주 과제라고 생각해서 인증에 필요한 통신 로직은 구현하지 않았습니다

### 3. env 파일 생성
```bash
echo "MOCK_WORKER_API_KEY=여기에_발급받은_API_Key_입력" > env
````
`"MOCK_WORKER_API_KEY=여기에_발급받은_API_Key_입력"` 부분을 발급받은 실제 키로 교체해야합니다
### 3. 애플리케이션 실행

```bash
docker-compose up --build
```

정상 실행 시 로그 흐름입니다

```
assignment-mysql  | ready for connections   ← MySQL 준비 완료
assignment-app    | Started PreTaskRealteathApplication  ← 앱 시작 완료
```

앱이 완전히 뜨기까지 약 30초~1분이 소요됩니다

- 서버 포트: `8080`
- API Base URL: `http://localhost:8080`

### 문제 발생 시 확인 방법

**앱이 뜨지 않는 경우**

```bash
docker logs assignment-app
docker logs assignment-mysql
```

**401 응답이 오는 경우**

API Key가 잘못 주입된 것입니다

```bash
docker-compose down
MOCK_WORKER_API_KEY=mock_xxxxxxxxxxxx docker-compose up
```

**포트 충돌 오류가 나는 경우**

```bash
# 8080 또는 3306 포트 사용 프로세스 확인 (Mac/Linux)
lsof -i :8080
lsof -i :3306
```

---

## API 명세

### 작업 제출

```
POST /jobs
Content-Type: application/json
```

요청 바디입니다

```json
{
  "imageUrl": "https://examplecom/imagejpg",
  "idempotencyKey": "unique-request-id-001"
}
```

응답: `202 Accepted`

```json
{
  "id": 1,
  "idempotencyKey": "unique-request-id-001",
  "status": "PENDING",
  "result": null,
  "createdAt": "2024-01-01T00:00:00",
  "updatedAt": "2024-01-01T00:00:00"
}
```

`Location` 헤더로 폴링 주소를 제공합니다

```
Location: /jobs/1
```

### 작업 단건 조회

```
GET /jobs/{id}
```

응답: `200 OK`

```json
{
  "id": 1,
  "idempotencyKey": "unique-request-id-001",
  "status": "COMPLETED",
  "result": "처리 결과 데이터",
  "createdAt": "2024-01-01T00:00:00",
  "updatedAt": "2024-01-01T00:00:10"
}
```

### 작업 목록 조회

```
GET /jobs?status=PENDING&page=0&size=20
```

| 파라미터 | 필수 | 기본값 | 설명 |
|---|---|---|---|
| status | X | 없음 (전체) | PENDING, PROCESSING, COMPLETED, FAILED |
| page | X | 0 | 페이지 번호 (0부터 시작) |
| size | X | 20 | 페이지 크기 (최대 100) |

응답: `200 OK`

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3,
  "hasNext": true
}
```

### 오류 응답 형식

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "imageUrl은 필수입니다"
}
```

| 상태코드 | 발생 상황 |
|---|---|
| 400 | 입력값 검증 실패, 잘못된 요청 형식 |
| 404 | 존재하지 않는 Job ID 조회 |
| 409 | 허용되지 않는 상태 전이 시도 |

---

## 상태 모델 설계

### 상태 전이 흐름

```
PENDING → PROCESSING → COMPLETED → FAILED
```

| 상태 | 의미 |
|---|---|
| PENDING | 작업 접수 완료 Mock Worker 전송 대기 중 |
| PROCESSING | Mock Worker에 전송됨 처리 중 |
| COMPLETED | 처리 성공 결과 조회 가능 |
| FAILED | 처리 실패 최종 상태 |

### PENDING을 내부 상태로 분리한 이유

Mock Worker의 상태 스펙(`PROCESSING`, `COMPLETED`, `FAILED`)과 내부 도메인 상태를 의도적으로 분리했습니다 클라이언트가 요청을 보낸 시점과 Mock Worker에 실제로 전송된 시점 사이의 간격이 존재하며, 이 구간을 명시적으로 표현하기 위해 `PENDING` 상태를 추가했습니다 `PENDING` 상태의 Job은 스케줄러가 다음 사이클에 재전송하므로 일시적 장애에 대한 자동 복구가 가능합니다

### 허용/차단 상태 전이

| 전이 | 허용 여부 | 이유 |
|---|---|---|
| PENDING → PROCESSING | ✅ | 정상 전송 경로 |
| PENDING → FAILED | ✅ | 4xx 등 재시도해도 무의미한 영구 실패 |
| PROCESSING → COMPLETED | ✅ | 정상 완료 |
| PROCESSING → FAILED | ✅ | 처리 중 실패 또는 타임아웃 |
| COMPLETED → 모든 상태 | ❌ | 최종 상태 보호 |
| FAILED → 모든 상태 | ❌ | 최종 상태 보호 |

---

## 외부 시스템 연동 방식

### DB 폴링 방식 선택

세 가지 방식을 검토했습니다

| 방식                           | 검토 결과                                     |
|------------------------------|-------------------------------------------|
| DB 폴링 (채택)                   | 추가 인프라 없음 재시작 복구 자동화 이 규모에 적합하다고 판단     |
| 메시지 큐 (Kafka/RabbitMQ/Redis) | 별도 인프라 필요 로컬 실행 조건 충족 어려움 오버엔지니어링이라고 판단 |
| Spring Batch                 | 대용량 일괄 처리에 특화 실시간 처리 요구사항에 부적합           |

### 전체 처리 흐름

```
[클라이언트]
    │ POST /jobs
    ▼
[서버 - JobService]
    │ DB에 PENDING으로 저장 후 202 응답
    ▼
[스케줄러 - dispatchPendingJobs, 1초 주기]
    │ PENDING Job 조회 → Mock Worker 전송
    ├→ 성공:          PENDING → PROCESSING
    ├→ 4xx 오류:      PENDING → FAILED (재시도해도 동일하게 실패)
    └→ 5xx/네트워크:  PENDING 유지 (다음 사이클 자동 재시도)
    ▼
[스케줄러 - pollProcessingJobs, 5초 주기]
    │ PROCESSING Job 상태 폴링
    ├→ COMPLETED: 결과 저장 후 COMPLETED
    ├→ FAILED:    FAILED 처리
    └→ PROCESSING: 다음 사이클 계속 폴링
    ▼
[스케줄러 - failTimeoutJobs, 1분 주기]
    │ PROCESSING 상태가 10분 이상 지속된 Job
    └→ 타임아웃 FAILED 처리
```

### Mock Worker 불안정성 대응

Mock Worker의 응답 시간과 안정성은 예측하기 어렵다는 점을 아래의 전략으로 대응했습니다

- **일시적 장애 (5xx, 네트워크 오류):** PENDING 유지 다음 스케줄러 사이클에서 자동 재시도
- **Rate Limit (429):** 해당 사이클 즉시 중단 Mock Worker 부하 증가 방지
- **무한 PROCESSING:** 10분 타임아웃 정책으로 FAILED 처리

---

## 중복 요청 처리 전략

### 락 방식 선택 근거

중복 요청 차단을 위한 락 방식을 다음과 같이 검토했습니다

비관적 락 / 낙관적 락:
락을 걸 대상 행이 이미 존재해야 동작합니다
신규 INSERT 중복 시나리오에는 적용이 불가능합니다

Redis 분산 락:
완벽한 중복 방지가 가능하나 Redis 인프라가 추가로 필요합니다
Redis 장애 시 락 자체가 불가능해지는 단일 장애 지점이 생깁니다
이 규모에서는 오버엔지니어링으로 판단했습니다

메시지 큐:
별도 인프라가 필요하고 구조가 복잡해집니다
실시간 202 응답 패턴과도 맞지 않습니다

DB Unique Constraint (채택):
추가 인프라 없이 DB가 원자적으로 중복을 보장합니다
애플리케이션 레벨 락보다 장애 포인트가 적고,
다중 인스턴스 환경에서도 DB 단에서 중복을 보장합니다

### 시나리오 1 — 순차 요청

첫 번째 요청 처리 완료 후 두 번째 요청이 오는 경우, `findByIdempotencyKey()`로 기존 Job을 조회하여 동일한 응답을 반환합니다

### 시나리오 2 — 동시 요청

두 요청이 동시에 들어와 `findByIdempotencyKey()`가 둘 다 null을 반환하는 경우, 두 요청 모두 `save()`를 시도합니다 DB의 `UNIQUE CONSTRAINT(idempotency_key)`가 하나를 차단하고, `DataIntegrityViolationException`을 catch하여 기존 데이터를 재조회해 반환합니다

### 선택 근거

분산 환경에서도 DB 단에서 중복을 보장하므로 별도의 분산 락 없이 안전합니다

---

## 실패 처리 전략

| 실패 유형 | 처리 방식 | 근거 |
|---|---|---|
| 네트워크 일시 오류 (5xx) | PENDING/PROCESSING 유지, 다음 사이클 재시도 | 일시적 장애이므로 재시도로 해결 가능 |
| 잘못된 요청 (4xx) | 즉시 FAILED | 재시도해도 동일하게 실패 |
| Rate Limit (429) | 해당 사이클 즉시 중단, 다음 사이클 재시도 | Mock Worker 부하 증가 방지 |
| Mock Worker 무응답 (Timeout) | PENDING/PROCESSING 유지, 다음 사이클 재시도 | 타임아웃은 일시적 과부하일 수 있음 |
| 장시간 PROCESSING (10분 초과) | FAILED 처리 | 좀비 작업 방지 타임아웃 기준은 Mock Worker 최대 응답 시간 고려 |

---

## 처리 보장 모델

"본 시스템은 **At-Least-Once (최소 한 번 처리) 보장 모델**을 따릅니다"

### 중복 전송이 발생하는 경우

Mock Worker에 전송 직후 서버가 재시작되면, 해당 Job은 DB에 PENDING으로 남아 있어 스케줄러가 재시작 후 다시 전송합니다

이 구조를 선택한 이유는 다음과 같습니다

- Mock Worker는 외부 서비스라 내부 동작을 제어할 수 없습니다
- AI 이미지 처리는 동일한 이미지를 보내도 동일한 결과가 나오므로 중복 전송이 최종 결과에 영향을 주지 않습니다
- 중복 전송을 완전히 막으려면 구조가 복잡해지는 데 비해 실질적인 이점이 없다고 판단했습니다

단, Mock Worker가 중복 요청에 대해 과금하는 구조라면 이 부분은 재검토가 필요합니다

---

## 서버 재시작 시 동작

### 데이터 정합성이 깨질 수 있는 지점

| 시점 | 상황 | 결과 |
|---|---|---|
| Mock Worker 전송 직후, DB 업데이트 전 재시작 | Job이 PENDING으로 남음 | 스케줄러 재시작 후 재전송 Mock Worker 중복 수신 가능 (At-Least-Once) |
| Mock Worker 완료 후, DB 업데이트 전 재시작 | Job이 PROCESSING으로 남음 | 스케줄러 재시작 후 상태를 다시 폴링하여 자동 복구 |

### 재시작 후 자동 복구 흐름

```
서버 재시작
    ↓
dispatchPendingJobs 실행
    └→ PENDING Job → Mock Worker 재전송 시도
    ↓
pollProcessingJobs 실행
    └→ PROCESSING Job → 상태 재폴링하여 COMPLETED/FAILED 업데이트
    ↓
failTimeoutJobs 실행
    └→ 10분 초과 PROCESSING Job → FAILED 처리
```

별도의 복구 로직 없이 스케줄러의 정상 동작만으로 자동 복구됩니다

---

## 트래픽 증가 시 병목 지점

### 스케줄러 단일 스레드 순차 처리

현재 스케줄러는 단일 스레드에서 최대 100건을 순차적으로 HTTP 요청합니다 Mock Worker 응답 지연이 길어지면 다음 작업들이 밀리게 됩니다

개선 방향: 스레드 풀을 두고 병렬로 요청하는 구조로 변경

### 데이터 누적에 따른 조회 성능 저하

시간이 지날수록 처리가 끝난 `COMPLETED`, `FAILED` 데이터가 대부분을 차지합니다 스케줄러가 매 사이클 `PENDING`, `PROCESSING` 상태를 조회할 때 처리된 데이터가 많아질수록 느려질 수 있습니다

개선 방향: 처리 완료된 Job을 주기적으로 별도 테이블로 옮기거나 삭제

### 서버를 여러 대로 늘릴 경우 스케줄러 중복 실행

현재 구조에서 서버를 2대 이상 띄우면 각 서버의 스케줄러가 동일한 PENDING Job을 동시에 조회해 Mock Worker에 중복으로 전송할 수 있습니다

개선 방향: DB 또는 외부 저장소를 이용한 분산 락 도입
