# 관측성 (Observability)

Sentry(에러/성능) + Grafana Cloud(Prometheus 메트릭 / Loki 로그)로 구성한다.
bank-app은 온프레미스(OpenStack)에서 **앱 + Alloy 사이드카**를 docker compose로 함께 기동한다.
Alloy 설정(`deploy/alloy/config.alloy`)은 hangang-pay-be와 **바이트 동일**하며, 차이는 `.env`로만 분기한다.

## 토폴로지

```
[On-prem / OpenStack]
  └─ bank-app(hangang-pay-bank) :8081   ← Besu RPC(10.10.1.x)
     app 컨테이너 + alloy 사이드카(같은 compose)
alloy → Grafana Cloud (Prometheus remote_write / Loki push, 공인 HTTPS 직접 egress)
app   → Sentry (예외/트랜잭션)
```

> EC2(platform) ⇄ bank-app은 WireGuard 터널을 쓰지만, 이는 앱-앱 트래픽 전용이며 Alloy→Grafana 경로와 무관하다.

## 라벨 체계

| 노드 | service | deploy | env | color | instance |
|---|---|---|---|---|---|
| 온프레 bank-app | hangang-pay-bank | onprem | prod | (빈값) | hostname |

Grafana 조회 예:
```logql
{service="hangang-pay-bank"}                  # bank 로그 전체
{service="hangang-pay-bank", level="error"}   # 에러 레벨만
```

## 배포 (온프레 수동)

```bash
docker build -t hangang-pay-bank:local .      # 레포 루트에서 이미지 빌드
cd deploy
cp .env.example .env                          # 값 채움 (DB/RPC/Sentry/Grafana 크리덴셜)
docker compose up -d                          # app(:8081) + alloy 기동
```

## 운영 환경변수 (`.env`)

`deploy/.env.example` 참고. 크리덴셜은 커밋 금지.

| 키 | 설명 |
|---|---|
| `SENTRY_DSN` | bank 전용 Sentry DSN(또는 공용 프로젝트). 미설정 시 비활성 |
| `GRAFANA_PROM_URL` / `_USER` / `_PASSWORD` | Prometheus remote_write |
| `GRAFANA_LOKI_URL` / `_USER` / `_PASSWORD` | Loki push |
| `ALLOY_SERVICE`=`hangang-pay-bank`, `DEPLOY_TARGET`=`onprem`, `ALLOY_ENV`=`prod`, `APP_SCRAPE_TARGET`=`app:8081`, `APP_CONTAINER_NAME`=`bank-app` | Alloy 라벨/타겟 |

> 컨테이너명은 `bank-app`/`bank-alloy`로 둔다(platform과 co-locate 시 이름 충돌 방지). compose **서비스명**은 `app`/`alloy` 유지라 스크랩 DNS `app:8081`은 그대로 동작한다.

## 활성 조건

- **Sentry**: `SENTRY_DSN`이 있을 때만 동작. `prod` 프로필에서 `environment`·`traces-sample-rate` 설정, `WARN` 이상 로그가 이벤트로 전송.
- **구조화 로깅(ECS JSON)**: `prod` 프로필에서만 활성. local/dev는 기본 포맷.
- **메트릭**: `/actuator/prometheus` 노출(`micrometer-registry-prometheus` 추가).
