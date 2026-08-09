# 03. DB 덤프

이 폴더에 운영 DB(PostgreSQL 15) 최신 덤프 파일을 업로드합니다.

- 파일명 규칙: `db_dump_YYYYMMDD.sql` (예: `db_dump_20260810.sql`)
- 대상 DB: 운영 Docker Compose 스택(`s15p11b102`)의 `postgres` 컨테이너 (postgres:15-alpine)

## 데모 데이터 적재 (DB가 비어 있을 때)

운영 DB가 비어 있으면 [demo_seed_data.sql](demo_seed_data.sql)로 시연·제출용 데모 데이터를 먼저 채웁니다.
(데모 회원 10명, 길드 2개 + 게시글·댓글, 5개 게임 전체 플레이 기록 23건 — 혼자하기·랜덤·초대·AI 대결, 주간 랭킹 배지 4건)

```bash
# 1) 로컬 PC에서 EC2로 파일 전송
scp -i <키페어.pem> exec/03_DB_덤프/demo_seed_data.sql ubuntu@<EC2 IP>:~/

# 2) EC2에서 postgres 컨테이너에 적재 (재실행해도 안전 — 데모 데이터만 지우고 다시 넣음)
docker exec -i s15p11b102-postgres-1 \
  psql -U <POSTGRES_USER> -d <POSTGRES_DB> -v ON_ERROR_STOP=1 < ~/demo_seed_data.sql

# 3) 확인 (users 10, games_results 23이면 정상)
docker exec s15p11b102-postgres-1 \
  psql -U <POSTGRES_USER> -d <POSTGRES_DB> \
  -c "SELECT count(*) FROM users WHERE email LIKE 'demo%@eyedontcare.local';" \
  -c "SELECT count(*) FROM games_results WHERE play_id::text LIKE 'dddddddd%';"
```

- 전제: 백엔드가 최소 1회 기동되어 스키마와 `games` 카탈로그가 생성된 상태 (자동으로 됨)
- 데모 회원은 `password NULL`·`demo*@eyedontcare.local` 형태의 전시용 계정이라 로그인은 불가하며, 랭킹·길드·전적 화면을 채우는 용도입니다. 실제 카카오 로그인 사용자와 충돌하지 않습니다.
- 적재 후 https://eyedontcare.shop 의 랭킹/커뮤니티 화면에서 데이터가 보이는지 확인하세요.

## 덤프 생성 방법 (EC2에서 실행)

운영 compose는 postgres 포트를 호스트에 노출하지 않으므로 `docker exec`로 덤프합니다.

```bash
# 컨테이너 이름 확인
docker compose -p s15p11b102 ps

# 전체 덤프 (스키마 + 데이터)
docker exec s15p11b102-postgres-1 \
  pg_dump -U <POSTGRES_USER> -d <POSTGRES_DB> --no-owner --no-privileges \
  > db_dump_$(date +%Y%m%d).sql
```

`<POSTGRES_USER>`, `<POSTGRES_DB>`는 운영 `.env`(Jenkins Secret file)의 값을 사용합니다.
덤프한 파일은 로컬로 가져와 이 폴더에 추가합니다.

```bash
scp -i <키페어.pem> ubuntu@<EC2 IP>:~/db_dump_*.sql exec/03_DB_덤프/
```

## 복원 방법

```bash
# 새 환경의 postgres 컨테이너 기동 후
docker exec -i s15p11b102-postgres-1 \
  psql -U <POSTGRES_USER> -d <POSTGRES_DB> < db_dump_YYYYMMDD.sql
```

## 참고

- 이 프로젝트는 마이그레이션 도구 없이 Hibernate `ddl-auto: update`로 스키마를 생성하고,
  `backend/src/main/resources/data.sql`이 게임 카탈로그(games)를 멱등 시드합니다.
  따라서 **빈 DB에서 백엔드를 기동해도 스키마·기본 데이터는 자동 구성**되며,
  덤프 복원은 사용자·플레이 기록 등 누적 데이터가 필요할 때 사용합니다.
- 주요 테이블: `users`(회원, 프로필), `games`(게임 카탈로그: 게임 × 플레이모드 × 난이도),
  `games_results` / `participants_`(플레이 결과·점수), 소셜 계정 연동 테이블 등.
