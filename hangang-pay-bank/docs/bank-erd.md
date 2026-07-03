# Bank ERD

```dbml
Table bank_institution {
  id bigint [pk, increment, note: '기관 식별자 (master, BE institution과 동일 ID)']
  institution_code varchar(20) [not null, unique, note: '기관 코드']
  institution_name varchar(100) [not null, note: '기관명 (예: 우리은행, 한국은행)']
  operator_wallet_address varchar(100) [note: 'operator(서명자) 지갑 주소 — 컨트랙트 tx 서명 (CBDC mint/burn 권한자)']
  operator_encrypted_private_key text [note: 'operator 지갑 개인키 (AES/GCM 암호화, mint/burn 서명)']
  rpc_endpoint varchar(255) [note: 'Besu RPC 엔드포인트']
  created_at datetime [not null, note: '생성 시각']
  updated_at datetime [not null, note: '수정 시각']
}

Table bank_account {
  id bigint [pk, increment, note: '식별자']
  institution_id bigint [not null, ref: > bank_institution.id, note: '소속 기관']
  account_number varchar(50) [not null, note: '계좌번호 (BE account.account_number와 매칭)']
  balance decimal(20,4) [not null, default: 0, note: '현재 잔액 (snapshot)']
  owner_name varchar(50) [note: '예금주명']
  created_at datetime [not null, note: '생성 시각']
  updated_at datetime [not null, note: '수정 시각']
}

Table account_ledger {
  id bigint [pk, increment, note: '식별자']
  idempotent_key varchar(36) [not null, unique, note: '멱등키 (BE transactionUuid)']
  institution_id bigint [not null, ref: > bank_institution.id, note: '소속 기관 (join 없이 기관별 조회용 역정규화)']
  bank_account_id bigint [not null, ref: > bank_account.id, note: '대상 계좌']
  status varchar(20) [not null, note: 'PENDING / SUCCESS / FAILED']
  ledger_type varchar(20) [not null, note: 'DEPOSIT(입금) / WITHDRAWAL(출금)']
  amount decimal(20,4) [not null, note: '거래 금액']
  balance_after decimal(20,4) [not null, note: '거래 후 잔액']
  created_at datetime [not null, note: '거래 시각']
  updated_at datetime [not null, note: '수정 시각']
}

Table bank_wallet {
  id bigint [pk, increment, note: '식별자']
  institution_id bigint [not null, ref: > bank_institution.id, note: '소속 기관']
  wallet_address varchar(100) [not null, unique, note: '지갑 주소 (BE wallet.address와 매칭)']
  balance decimal(20,4) [not null, default: 0, note: '토큰 잔액 (Bank DB source of truth)']
  encrypted_private_key text [not null, note: '지갑 개인키 (Bank 커스터디)']
  created_at datetime [not null, note: '생성 시각']
  updated_at datetime [not null, note: '수정 시각']
}

Table wallet_ledger {
  id bigint [pk, increment, note: '식별자']
  transaction_uuid varchar(36) [not null, note: '거래 식별자 (BE transactionUuid)']
  bank_wallet_id bigint [not null, ref: > bank_wallet.id, note: '대상 지갑']
  direction varchar(10) [not null, note: 'DEBIT(출금) / CREDIT(입금)']
  status varchar(20) [not null, note: 'PENDING / SUCCESS / FAILED']
  amount decimal(20,4) [not null, note: '거래 금액']
  confirmed_at datetime [note: 'Bank DB 커밋 확정 시각 (FAILED 시 null)']
  created_at datetime [not null, note: '생성 시각']
  updated_at datetime [not null, note: '수정 시각']

  indexes {
    (transaction_uuid, bank_wallet_id) [unique, note: '복식부기 idempotency 키']
  }
}

Table contract {
  id bigint [pk, increment, note: '식별자']
  institution_id bigint [not null, ref: > bank_institution.id, note: '컨트랙트 배포 기관']
  name varchar(20) [not null, note: 'CBDC / DEPOSIT_TOKEN / SETTLEMENT / LOCAL_CURRENCY']
  address char(42) [not null, unique, note: '컨트랙트 주소 (0x... 42자)']
  created_at datetime [not null, note: '생성 시각']
  updated_at datetime [not null, note: '수정 시각']
}

Table blockchain_ledger {
  id bigint [pk, increment, note: '식별자']
  idempotent_key varchar(36) [not null, unique, note: '멱등키 (BE transactionUuid)']
  institution_id bigint [not null, ref: > bank_institution.id, note: '서명 기관']
  type varchar(20) [not null, note: 'PAYMENT / CANCEL / CHARGE / EXCHANGE']
  tx_hash varchar(100) [note: '블록체인 tx 해시 (submit 후 채워짐)']
  block_number bigint [note: '채굴된 블록 번호']
  status varchar(20) [not null, note: 'PENDING / SUBMITTED / SUCCESS / FAILED']
  confirmed_at datetime [note: '온체인 확정 시각']
  created_at datetime [not null, note: '생성 시각']
  updated_at datetime [not null, note: '수정 시각']
}

Table blockchain_outbox {
  id bigint [pk, increment, note: '식별자']
  blockchain_ledger_id bigint [not null, ref: > blockchain_ledger.id, note: '연결된 BlockchainLedger']
  message_id varchar(36) [not null, unique, note: '안정적 메시지 식별자 (재시도해도 동일)']
  transaction_uuid varchar(36) [not null, note: '거래 식별자 (BE transactionUuid) — 메시지 빌드·로깅 편의용 역정규화']
  type varchar(20) [not null, note: 'PAYMENT / CANCEL / CHARGE / EXCHANGE']
  payload text [not null, note: '컨트랙트 호출 인자 JSON (from/to address, amount 등)']
  status varchar(20) [not null, note: 'NEW / SENT / FAILED']
  retry_count int [not null, default: 0, note: 'MQ 발행 재시도 횟수']
  created_at datetime [not null, note: '생성 시각']
  updated_at datetime [not null, note: '수정 시각']
}
```
