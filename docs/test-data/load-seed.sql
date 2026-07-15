-- 부하 테스트용 mock 시드: 300 소비자 + 50 가맹점 (hangang_pay = BE 메인 DB)
--
-- 목적: 결제 처리량 부하(v0 NOWAIT 비관락 vs v1 Redis 분산락)를 "다유저·다가맹점" 현실 부하로 측정.
--       payer wallet 단위로 락이 걸리는 v0의 경합이 얼마나 분산되는지 보려면 서로 다른 payer가 많아야 한다.
--
-- 로그인 자격: 모든 mock 유저의 password/PIN 은 기존 시드 유저(01012345678)의 해시를 그대로 복사한다.
--   -> password = "password", paymentPin = "123456" (LocalDataInitializer 값). k6가 이 값으로 로그인/결제.
--
-- 결정적 식별자(형광펜): k6 스크립트(payment-throughput.js)가 같은 공식으로 생성하므로 반드시 일치시킬 것.
--   소비자 phone   : CONCAT('0155', LPAD(n,7,'0'))   n=1..300   -> 01550000001 ..
--   소비자 party.id: 500000+n                                     -> 500001 ..
--   가맹점 party.id: 600000+n                          n=1..50    -> 600001 ..  (k6 intent 의 merchantPartyId)
--
-- 실행 (한글 없음이라 charset 옵션 불필요하지만 통일):
--   mysql --default-character-set=utf8mb4 -u <user> -p hangang_pay < docs/test-data/load-seed.sql
--
-- 한 번만 실행. 재실행/정리는 파일 하단 CLEANUP 참고.

USE hangang_pay;

-- 기존 시드 유저의 해시·institution 을 복사(값이 없으면 시드 유저부터 만들 것).
SET @pw   := (SELECT password_hash    FROM users WHERE phone_number = '01012345678' LIMIT 1);
SET @pin  := (SELECT payment_pin_hash FROM users WHERE phone_number = '01012345678' LIMIT 1);
SET @inst := (SELECT w.institution_id FROM wallet w
              JOIN users u ON u.party_id = w.party_id
              WHERE u.phone_number = '01012345678' LIMIT 1);
SELECT (@pw IS NOT NULL) AS pw_ok, (@pin IS NOT NULL) AS pin_ok, @inst AS institution_id;

-- ============================================================
-- 1) 소비자 300명: party(USER) -> users -> wallet
-- ============================================================
INSERT INTO party (id, party_type, created_at, updated_at)
WITH RECURSIVE s AS (SELECT 1 n UNION ALL SELECT n+1 FROM s WHERE n < 300)
SELECT 500000 + n, 'USER', NOW(), NOW() FROM s;

INSERT INTO users (id, party_id, username, phone_number, password_hash, payment_pin_hash, created_at, updated_at)
WITH RECURSIVE s AS (SELECT 1 n UNION ALL SELECT n+1 FROM s WHERE n < 300)
SELECT 500000 + n, 500000 + n,
       CONCAT('loaduser', n),
       CONCAT('0155', LPAD(n, 7, '0')),
       @pw, @pin, NOW(), NOW()
FROM s;

INSERT INTO wallet (id, party_id, institution_id, address, created_at, updated_at)
WITH RECURSIVE s AS (SELECT 1 n UNION ALL SELECT n+1 FROM s WHERE n < 300)
SELECT 500000 + n, 500000 + n, @inst,
       CONCAT('0xa', LPAD(HEX(500000 + n), 39, '0')),
       NOW(), NOW()
FROM s;

-- ============================================================
-- 2) 가맹점 50개: party(MERCHANT) -> merchant -> wallet
--    merchant.payment_pin_hash 는 NOT NULL 이라 반드시 채운다.
-- ============================================================
INSERT INTO party (id, party_type, created_at, updated_at)
WITH RECURSIVE s AS (SELECT 1 n UNION ALL SELECT n+1 FROM s WHERE n < 50)
SELECT 600000 + n, 'MERCHANT', NOW(), NOW() FROM s;

INSERT INTO merchant (id, party_id, username, merchant_name, owner_name,
                      phone_number, business_number, address,
                      password_hash, payment_pin_hash, created_at, updated_at)
WITH RECURSIVE s AS (SELECT 1 n UNION ALL SELECT n+1 FROM s WHERE n < 50)
SELECT 600000 + n, 600000 + n,
       CONCAT('loadmerch', n),
       CONCAT('LoadShop', n),
       CONCAT('Owner', n),
       CONCAT('0266', LPAD(n, 7, '0')),
       CONCAT('9', LPAD(n, 9, '0')),
       'Seoul',
       @pw, @pin, NOW(), NOW()
FROM s;

INSERT INTO wallet (id, party_id, institution_id, address, created_at, updated_at)
WITH RECURSIVE s AS (SELECT 1 n UNION ALL SELECT n+1 FROM s WHERE n < 50)
SELECT 600000 + n, 600000 + n, @inst,
       CONCAT('0xb', LPAD(HEX(600000 + n), 39, '0')),
       NOW(), NOW()
FROM s;

-- ============================================================
-- 검증
-- ============================================================
SELECT COUNT(*) AS mock_users     FROM users    WHERE id BETWEEN 500001 AND 500300;
SELECT COUNT(*) AS mock_merchants FROM merchant WHERE id BETWEEN 600001 AND 600050;
SELECT COUNT(*) AS mock_wallets   FROM wallet   WHERE id BETWEEN 500001 AND 500300 OR id BETWEEN 600001 AND 600050;
-- 로그인/결제 스모크용 첫 유저·첫 가맹점
SELECT id, party_id, phone_number FROM users WHERE id = 500001;
SELECT id, party_id, merchant_name FROM merchant WHERE id = 600001;

-- ============================================================
-- CLEANUP (실험 종료 후 정리. 부하로 생긴 transaction 먼저 지워야 FK 안 걸림)
-- ============================================================
-- DELETE FROM transaction WHERE from_party_id BETWEEN 500001 AND 500300
--     OR to_party_id BETWEEN 600001 AND 600050;
-- DELETE FROM wallet   WHERE party_id BETWEEN 500001 AND 500300 OR party_id BETWEEN 600001 AND 600050;
-- DELETE FROM users    WHERE id BETWEEN 500001 AND 500300;
-- DELETE FROM merchant WHERE id BETWEEN 600001 AND 600050;
-- DELETE FROM party    WHERE id BETWEEN 500001 AND 500300 OR id BETWEEN 600001 AND 600050;
