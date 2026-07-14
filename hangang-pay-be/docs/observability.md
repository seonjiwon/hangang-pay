# 관측성 (Observability)

Sentry(에러/성능) + Grafana Cloud(Prometheus 메트릭 / Loki 로그)로 구성한다.
메트릭·로그는 **앱과 1:1 Alloy 사이드카**가 수집해 Grafana Cloud로 직접 push 한다.

## 토폴로지

```
[AWS EC2 / cloud]  platform(hangang-pay-be) :8080
  ├─ Blue  그룹 2대 (이중화)
  └─ Green 그룹 2대 (이중화)
[On-prem / OpenStack]
  └─ platform 테스트 서버 (hangang-pay-be) :8080
각 노드: app 컨테이너 + alloy 사이드카(같은 compose)
alloy → Grafana Cloud (Prometheus remote_write / Loki push, 공인 HTTPS)
app   → Sentry (예외/트랜잭션)
```

- Alloy는 stateless push 모델 → 오토스케일링/이중화와 충돌 없음(중앙 스크랩 타겟 목록 불필요).
- `deploy/alloy/config.alloy`는 모든 값을 환경변수로 외부화 → **bank 레포와 바이트 동일**, 서버 차이는 `.env`로만 분기.

## 라벨 체계

| 노드 | service | deploy | env | color | instance |
|---|---|---|---|---|---|
| EC2 Blue ×2 | hangang-pay-be | cloud | prod | blue | EC2 instance-id |
| EC2 Green ×2 | hangang-pay-be | cloud | prod | green | EC2 instance-id |
| 온프레 platform 테스트 | hangang-pay-be | onprem | test | (빈값) | hostname |

Grafana 조회 예:
```logql
{service="hangang-pay-be", color="blue"}     # EC2 Blue 2대
{service="hangang-pay-be", deploy="onprem"}   # 온프레 platform 테스트
```

## 운영 환경변수 (`.env`)

EC2는 `start.sh`가 SSM(`/hangang-pay/be/prod/*`) + IMDS로 자동 생성한다. 온프레는 `deploy/.env.example` 복사.

크리덴셜(시크릿, 커밋 금지):

| 키 | 설명 |
|---|---|
| `SENTRY_DSN` | Sentry 프로젝트 DSN. 미설정 시 Sentry 비활성 |
| `SENTRY_RELEASE` | (선택) 배포 버전 라벨 |
| `GRAFANA_PROM_URL` / `_USER` / `_PASSWORD` | Grafana Cloud Prometheus remote_write |
| `GRAFANA_LOKI_URL` / `_USER` / `_PASSWORD` | Grafana Cloud Loki push |

라벨/타겟(비밀 아님):

| 키 | 값 |
|---|---|
| `ALLOY_SERVICE` | `hangang-pay-be` |
| `DEPLOY_TARGET` | `cloud` / `onprem` |
| `ALLOY_ENV` | `prod` / `test` |
| `ALLOY_COLOR` | `blue` / `green` / (빈값) |
| `APP_SCRAPE_TARGET` | `app:8080` (compose 서비스명 기준) |
| `APP_CONTAINER_NAME` | `app` (로그 필터용 컨테이너명) |
| `INSTANCE_ID` | EC2 instance-id / hostname |

> 사전 준비(담당자 수동): Grafana Cloud → Connections에서 Prometheus/Loki endpoint·API key 발급, Sentry 프로젝트 생성 후 DSN 발급, 위 키들을 SSM/`.env`에 등록.

## 활성 조건

- **Sentry**: `SENTRY_DSN`이 있을 때만 동작. `prod` 프로필에서 `environment`·`traces-sample-rate` 설정. `WARN` 이상 로그가 이벤트로 전송된다.
- **콘솔 로깅**: 모든 프로필이 사람이 읽는 평문 포맷. Alloy가 stdout을 그대로 수집해 Loki로 push하며, `loki.process`의 `stage.regexp`가 라인에서 레벨(INFO/WARN/ERROR 등)을 뽑아 `level` 라벨로 승격한다.
- **메트릭**: `/actuator/prometheus` (모든 프로필 노출).

## 배포 흐름 (EC2 / CodeDeploy)

1. `install.sh`: docker compose 플러그인 확인, ECR 로그인, app·alloy 이미지 pull, `/opt/hangang-pay-be` 정리.
2. Install: `docker-compose.yml`·`alloy/`를 `/opt/hangang-pay-be`로 복사(bind mount 안정 경로 확보).
3. `start.sh`: SSM+IMDS로 `.env` 생성 → `docker compose up -d`.
4. `validate.sh`: 앱 `/actuator/health` 확인(Alloy는 게이트 아님).
