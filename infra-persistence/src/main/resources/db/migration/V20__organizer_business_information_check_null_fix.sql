-- 주최자 사업자 정보 CHECK가 사업자 여부 없이 채워진 사업자 정보를 막게 다시 만든다 (#205)
-- 근거: V18 "주최자의 지금 사업자 여부 · 사업자 정보 · 정산 계좌" 주석(심사 중인 주최자는 비어 있다, 사업자면 사업자 정보 다섯 칸을 모두
--       채우고 비사업자면 모두 비운다), docs/01 "신청 항목"(사업자/비사업자)
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V21__...)을 추가한다.

-- V18의 organizer_business_information_ck는 사업자 여부(business_status)가 NULL인데 사업자 정보 다섯 칸이 모두 채워진 줄을 받았다.
-- 첫 갈래의 business_status = 'BUSINESS'가 NULL이 되어 그 갈래가 NULL(NULL AND TRUE)이 되고, 둘째 갈래는 FALSE라 식 전체가
-- NULL OR FALSE = NULL이 된다 — PostgreSQL CHECK는 식이 NULL이면 통과시킨다. 저장소는 사업자 여부로 먼저 걸러 읽으므로 그런 줄은 '사업자
-- 정보 없음'으로 보여, 개인정보 다섯 칸이 화면에 안 보인 채 표에 남는다.
-- NULL이 될 수 있는 business_status를 첫 갈래에서 IS NOT NULL로 먼저 갈라 식이 NULL이 되지 않게 같은 이름으로 다시 만든다(둘째 갈래는
-- 이미 IS NULL로 먼저 가른다). 규칙은 그대로다: 심사 중(사업자 여부 없음)이면 다섯 칸이 모두 비고, 사업자면 모두 채우고, 비사업자면 모두 비운다.
-- 지금 쓰기 경로(승인 때 신청서 값 옮겨 적기 · 변경 요청 승인 · V18 백필)는 사업자 여부를 늘 채우므로 새 CHECK에 어긋나는 줄은 없다.
-- 직접 SQL로 넣은 어긋난 줄이 있으면 이 마이그레이션이 실패한다 — 말없이 지우지 않고 사람이 보게 한다.
--
-- 같은 표의 나머지 CHECK는 그대로 둔다. organizer_status_ck는 NOT NULL 칸이다. organizer_business_status_ck(IN)와
-- organizer_introduction_not_empty_ck(char_length)는 칸이 NULL이면 식이 NULL이 되어 통과하는데, 사업자 여부 없음(심사 중)과 소개 없음은
-- 받아야 하는 값이라 뜻대로다. organizer_settlement_account_ck는 IS [NOT] NULL만 써 식이 NULL이 되지 않는다.
ALTER TABLE organizer DROP CONSTRAINT organizer_business_information_ck;

ALTER TABLE organizer ADD CONSTRAINT organizer_business_information_ck CHECK (
    (business_status IS NOT NULL
        AND business_status = 'BUSINESS'
        AND business_trade_name IS NOT NULL
        AND business_representative_name IS NOT NULL
        AND business_address IS NOT NULL
        AND business_phone_number IS NOT NULL
        AND business_registration_number IS NOT NULL)
    OR ((business_status IS NULL OR business_status = 'NON_BUSINESS')
        AND business_trade_name IS NULL
        AND business_representative_name IS NULL
        AND business_address IS NULL
        AND business_phone_number IS NULL
        AND business_registration_number IS NULL));
