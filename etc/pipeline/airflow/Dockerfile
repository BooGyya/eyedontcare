# Airflow + dbt-postgres. DAG가 BashOperator로 dbt를 돌리고,
# data_quality_check가 psycopg2로 reconcile 결과를 조회한다.
FROM apache/airflow:2.9.3

USER airflow
RUN pip install --no-cache-dir \
    "dbt-postgres==1.8.*" \
    "psycopg2-binary"
