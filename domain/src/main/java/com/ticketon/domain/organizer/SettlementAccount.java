package com.ticketon.domain.organizer;

import com.ticketon.domain.policy.ProposedPolicy;
import java.util.Objects;

/**
 * 정산 계좌 — 신청서의 필수 항목. 사람이 아니라 주최자에 붙는다(docs/01 "신청 항목", "수집 정보 — 주최자 운영진").
 * <p>
 * 테스트 결제라 실제 송금은 없지만 값은 받고 저장한다. 승인 뒤 바꾸려면 소유자가 요청하고 관리자가 확인한 뒤 적용된다 — 운영자로 들어온
 * 사람이 소유자 자리를 넘겨받고 계좌를 바꿔 정산금을 가로채지 못하게 하기 위해서다(docs/01 "가로채기를 막는 장치", docs/11 "정산 계좌 변경").
 * <p>
 * 문서에는 "정산 계좌"만 있어, 송금에 필요한 은행 · 계좌번호 · 예금주 셋으로 나눴다. 형식은 은행마다 달라 비어 있지 않은지만 본다.
 * <p>
 * 민감한 값이라 {@link #toString}에는 값을 싣지 않는다 — 예외 설명이나 로그로 새지 않게. 같은지는 값으로 본다. 소유자 화면처럼 계좌번호
 * 전체를 보일 까닭이 없는 곳에는 {@link #maskedAccountNumber 가린 계좌번호}를 쓴다.
 *
 * @param bankName      은행. 앞뒤 공백을 걷는다.
 * @param accountNumber 계좌번호. 앞뒤 공백을 걷고, 적은 그대로 둔다.
 * @param accountHolder 예금주. 앞뒤 공백을 걷는다.
 */
public record SettlementAccount(String bankName, String accountNumber, String accountHolder) {

    private static final char MASK = '*';

    public SettlementAccount {
        bankName = requireText(bankName, "은행");
        accountNumber = requireText(accountNumber, "계좌번호");
        accountHolder = requireText(accountHolder, "예금주");
    }

    /**
     * 마지막 숫자 몇 개({@link ProposedPolicy#SETTLEMENT_ACCOUNT_VISIBLE_TRAILING_DIGITS})만 남기고 나머지 숫자를 {@code *}로 가린 계좌번호
     * — 하이픈 · 공백 같은 숫자 아닌 글자는 적은 그대로 둔다(예: {@code 123456-78-901234} → {@code ******-**-**1234}). 숫자가 그 수 이하면
     * 모두 가린다. 소유자가 "어느 계좌인지" 알아볼 만큼만 보인다.
     */
    public String maskedAccountNumber() {
        int visibleDigits = ProposedPolicy.SETTLEMENT_ACCOUNT_VISIBLE_TRAILING_DIGITS;
        long digitCount = accountNumber.chars().filter(Character::isDigit).count();
        long digitsToMask = digitCount > visibleDigits ? digitCount - visibleDigits : digitCount;
        StringBuilder masked = new StringBuilder(accountNumber.length());
        long maskedDigits = 0;
        for (char character : accountNumber.toCharArray()) {
            if (Character.isDigit(character) && maskedDigits < digitsToMask) {
                masked.append(MASK);
                maskedDigits++;
            } else {
                masked.append(character);
            }
        }
        return masked.toString();
    }

    @Override
    public String toString() {
        return "SettlementAccount[****]";
    }

    private static String requireText(String value, String itemLabel) {
        Objects.requireNonNull(value, itemLabel);
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(itemLabel + "이(가) 비어 있습니다");
        }
        return stripped;
    }
}
