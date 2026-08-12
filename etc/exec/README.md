# exec — 포팅 매뉴얼

eye-dont-care (S15P11B102) 포팅 매뉴얼 폴더입니다.

| 문서 | 내용 |
| --- | --- |
| [01_빌드_및_배포_매뉴얼.md](01_빌드_및_배포_매뉴얼.md) | JVM/웹서버/WAS 종류·설정·버전, 빌드 환경변수 상세, 배포 특이사항, DB 접속·프로퍼티 정의 파일 목록 |
| [02_외부_서비스_정보.md](02_외부_서비스_정보.md) | 카카오 로그인, OpenVidu/LiveKit, SSAFY GMS, SeeSo, Mattermost 가입·활용 정보 |
| [03_DB_덤프/](03_DB_덤프/) | 운영 DB(PostgreSQL 15) 덤프 최신본 + 생성/복원 방법 |
| [04_시연_시나리오.md](04_시연_시나리오.md) | 시연 순서에 따른 화면별·실행별(클릭 위치) 상세 설명 |

> 실제 Secret 값(카카오 키, JWT 시크릿, LiveKit 키, GMS 키 등)은 이 폴더에 포함하지 않으며,
> Jenkins Secret file(`s15p11b102-prod-env`)로 관리합니다. 키 목록은 루트 `.env.example`을 참고하세요.
