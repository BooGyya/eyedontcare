# eye dont care — 데이터 파이프라인

게임 결과를 **감사(reconcile) + 분석(analytics)** 하는 **배치 ELT** 파이프라인.
서빙은 실시간(운영 DB 직접, `participants_.score` 컬럼·JSONB)이라 **배치는 서빙 경로가 아님**.
소스는 운영 Postgres(`public`), 결과는 `analytics` 스키마에 물질화한다.

```
운영 public                dbt (analytics 스키마)
 games_results ─┐   stg_game_results ─┐
 games ─────────┘                     ├─▶ fact_game_play ─┬─▶ agg_user_game_stats   [분석]
 participants_ ──▶ stg_participants ──┘   (score 컬럼)     └─▶ game_daily_stats       [분석]
 (game_result JSONB) ───────────────────▶ reconcile_score  (사본 vs 원본 대조)        [감사]
```

## 구조
```
pipeline/
  dbt/
    dbt_project.yml        # dbt 설정 (staging=view, marts=table)
    profiles.yml           # DB 연결(환경변수 주입, schema=analytics)
    models/
      staging/
        _sources.yml
        stg_game_results.sql     # 결과+게임 메타
        stg_participants.sql     # 참가자 + score 컬럼
      marts/
        fact_game_play.sql       # 참가자 1행 (score 컬럼 + 게임 메타)
        agg_user_game_stats.sql  # [분석] 유저×게임 누적
        game_daily_stats.sql     # [분석] 게임·일자별 지표
        reconcile_score.sql      # [감사] score 사본 vs JSONB 원본 대조
        _marts.yml               # 데이터 품질 테스트
  sample/
    sample_game_data.sql   # 개발/검증용 샘플(실데이터 없을 때)
```

## 감사(reconcile) 규칙
`participants_.score`(실시간 사본) vs `game_result` JSONB(원본 기대값)를 대조해 불일치만 남긴다(정상이면 0행).
- `MISSING` : 사본이 비었는데 원본엔 점수 있음 → 안전 백필 후보
- `MISMATCH`: 사본 ≠ 원본 → 사람 검토
- 자동 교정은 안전 백필만, 나머지는 리포트(사람 검토).

## 로컬 실행 (compose Postgres 대상)
```bash
# 1) 스키마 준비(백엔드가 생성) + 샘플 적재
docker compose -f compose.dev.yml up -d postgres backend
docker exec -i <postgres> psql -U backend -d backend < pipeline/sample/sample_game_data.sql
# 2) dbt 실행
cd pipeline/dbt && dbt run --profiles-dir . && dbt test --profiles-dir .
```
연결값은 `DBT_HOST/PORT/USER/PASSWORD/DBNAME/SCHEMA` 환경변수로 주입(기본값 로컬 compose).

## 단계
- Phase A(완료): 백엔드 `participants_.score` 컬럼 + 서빙 컬럼 기반 전환
- **Phase B(현재)**: staging + fact + **분석 마트(agg·daily)** + **감사(reconcile)** + dbt tests
- Phase C: 야간 오케스트레이션(Airflow DAG 또는 cron), 멱등·스케줄
