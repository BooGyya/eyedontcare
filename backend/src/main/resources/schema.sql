-- ProfileImageCode 확장에 맞춰 기존 CHECK 제약조건을 갱신한다.
-- Hibernate ddl-auto:update는 기존 enum CHECK 제약조건 변경을 보장하지 않는다.

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_profile_img_code_check;

ALTER TABLE users
    ADD CONSTRAINT users_profile_img_code_check
    CHECK (
        profile_img_code IN (
            'PROFILE_1',
            'PROFILE_2',
            'PROFILE_3',
            'PROFILE_4',
            'PROFILE_5',
            'PROFILE_6',
            'PROFILE_7',
            'PROFILE_8'
        )
    );
