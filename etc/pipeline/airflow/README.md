# Airflow 오케스트레이션 (Phase C)

야간 배치를 Airflow DAG로 돌린다. **감사(reconcile) + 분석(mart)** 을 매일 갱신한다.
서빙은 실시간(컬럼) 그대로 — 이 배치는 서빙 경로가 아니다.

## DAG: `game_analytics_pipeline`
```
dbt_run  ──▶  dbt_test  ──▶  data_quality_check
(모델 빌드)   (품질 테스트)   (reconcile 불일치 확인·경고)
```
- 스케줄: **매일 KST 03:00**(`0 18 * * *` UTC), `catchup=False`, 재시도 1회
- 멱등: dbt가 매번 재빌드 → 몇 번 돌려도 결과 동일
- `data_quality_check`: `analytics.reconcile_score` 행수를 세어 불일치가 있으면 **경고 로그**(실패시키지 않음 — 사람 검토 원칙)

## 구성
```
pipeline/airflow/
  Dockerfile                    # apache/airflow + dbt-postgres + psycopg2
  docker-compose.airflow.yml    # 로컬 데모(standalone), 게임 Postgres 네트워크에 연결
  dags/
    game_analytics_pipeline.py  # DAG
```
dbt 프로젝트는 `../dbt`를 컨테이너 `/opt/airflow/dbt`로 마운트해서 그대로 사용한다.

## 로컬 실행 (데모)
```bash
# 1) 게임 Postgres 기동(스키마·시드 위해 backend도) — compose.dev.yml
docker compose -f compose.dev.yml up -d postgres backend
docker exec -i <postgres> psql -U backend -d backend < pipeline/sample/sample_game_data.sql

# 2) Airflow 기동
cd pipeline/airflow
docker compose -f docker-compose.airflow.yml up -d --build
# 웹 UI: http://localhost:8080 (standalone이 콘솔에 admin 비밀번호 출력)

# 3) DAG 실행(수동 트리거)
docker compose -f docker-compose.airflow.yml exec airflow \
  airflow dags trigger game_analytics_pipeline
```

## 운영 시 (참고)
- 데모는 `standalone`(SQLite 메타DB). 운영은 **LocalExecutor/Celery + Postgres 메타DB**로 승격 권장.
- 연결 정보(`DBT_*`)는 compose env가 아니라 시크릿/Connection으로 주입.
- 알림: 불일치 발생 시 Slack/메일 알림 태스크 추가 가능.

## 대안
- Airflow가 부담이면 동일 흐름을 **cron**으로도 가능: `0 18 * * * cd pipeline/dbt && dbt run && dbt test`. 로직은 같고 트리거만 다름.
