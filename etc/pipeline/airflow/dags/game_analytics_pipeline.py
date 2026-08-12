"""게임 데이터 야간 배치 — dbt 감사(reconcile) + 분석(mart) 갱신.

- 스케줄: 매일 KST 03:00 (= UTC 18:00). catchup 없음, 재시도 1회.
- 흐름: dbt run(빌드) -> dbt test(품질) -> data_quality_check(reconcile 불일치 확인).
- 서빙과 무관: 운영 테이블을 읽어 analytics 스키마를 갱신할 뿐(실시간 서빙은 컬럼 기반).
- 멱등: dbt는 CREATE ... AS로 매번 재빌드하므로 몇 번 돌려도 결과 동일.
"""
from __future__ import annotations

import os
from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.bash import BashOperator
from airflow.operators.python import PythonOperator

DBT_DIR = os.environ.get("DBT_PROJECT_DIR", "/opt/airflow/dbt")


def check_data_quality() -> int:
    """reconcile_score(사본 vs 원본 불일치)를 세어 로그로 남긴다.

    불일치가 있어도 파이프라인을 실패시키지 않고 경고만 한다(사람 검토 원칙).
    """
    import psycopg2

    connection = psycopg2.connect(
        host=os.environ["DBT_HOST"],
        port=int(os.environ.get("DBT_PORT", "5432")),
        user=os.environ["DBT_USER"],
        password=os.environ["DBT_PASSWORD"],
        dbname=os.environ["DBT_DBNAME"],
    )
    try:
        with connection.cursor() as cursor:
            cursor.execute("select count(*) from analytics.reconcile_score")
            issues = cursor.fetchone()[0]
    finally:
        connection.close()

    if issues:
        print(f"[data-quality] reconcile 불일치 {issues}건 — 리포트 검토 필요")
    else:
        print("[data-quality] 불일치 없음(사본=원본 일치)")
    return issues


default_args = {
    "retries": 1,
    "retry_delay": timedelta(minutes=5),
}

with DAG(
    dag_id="game_analytics_pipeline",
    description="야간 dbt 감사+분석 배치",
    schedule="0 18 * * *",  # KST 03:00
    start_date=datetime(2026, 1, 1),
    catchup=False,
    default_args=default_args,
    tags=["dbt", "analytics", "reconcile"],
) as dag:
    dbt_run = BashOperator(
        task_id="dbt_run",
        bash_command=f"cd {DBT_DIR} && dbt run --profiles-dir .",
    )

    dbt_test = BashOperator(
        task_id="dbt_test",
        bash_command=f"cd {DBT_DIR} && dbt test --profiles-dir .",
    )

    data_quality_check = PythonOperator(
        task_id="data_quality_check",
        python_callable=check_data_quality,
    )

    dbt_run >> dbt_test >> data_quality_check
