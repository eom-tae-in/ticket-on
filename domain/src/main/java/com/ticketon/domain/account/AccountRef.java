package com.ticketon.domain.account;

import java.util.Objects;

/**
 * 로그인 주체 — 어느 종류의 몇 번 계정인지.
 * <p>
 * 계정 id는 종류마다 따로 매기므로 id만으로는 계정을 가리킬 수 없다. 관객 7번과 운영진 7번은 다른 사람일 수 있고,
 * 한 사람이 같은 이메일로 만든 관객 계정과 주최자 쪽 로그인도 서로 다른 주체다(docs/01 "같은 이메일로 양쪽 가입").
 * <p>
 * {@link #principalName() 주체 이름}은 이 값을 글자 하나로 적은 "종류:id"(예: {@code AUDIENCE:7})다. 로그인 세션은 이 이름으로
 * 주체별로 묶이고, 관리자 정지·탈퇴·검표 계정 비밀번호 재발급 때 이 이름으로 그 주체의 세션을 모두 끊는다(docs/01).
 *
 * @param kind 계정 종류
 * @param id   그 종류 안에서의 계정 id (1 이상)
 */
public record AccountRef(AccountKind kind, long id) {

    private static final String SEPARATOR = ":";

    public AccountRef {
        Objects.requireNonNull(kind, "kind");
        if (id < 1) {
            throw new IllegalArgumentException("계정 id는 1 이상이어야 합니다: " + id);
        }
    }

    public static AccountRef of(AccountKind kind, long id) {
        return new AccountRef(kind, id);
    }

    /**
     * 주체 이름 "종류:id"를 다시 주체로 읽는다. {@link #principalName()}이 만든 모양만 받는다.
     *
     * @throws IllegalArgumentException 모양이 다르면(모르는 종류, 숫자가 아닌 id, 앞에 0을 붙인 id 등)
     */
    public static AccountRef fromPrincipalName(String principalName) {
        Objects.requireNonNull(principalName, "principalName");
        int separatorIndex = principalName.indexOf(SEPARATOR);
        if (separatorIndex < 0) {
            throw invalidPrincipalName(principalName);
        }
        AccountRef account = parse(principalName.substring(0, separatorIndex), principalName.substring(separatorIndex + 1),
                principalName);
        if (!account.principalName().equals(principalName)) {
            throw invalidPrincipalName(principalName);
        }
        return account;
    }

    /** 주체 이름 "종류:id" (예: {@code AUDIENCE:7}). */
    public String principalName() {
        return kind.name() + SEPARATOR + id;
    }

    private static AccountRef parse(String kindName, String idText, String principalName) {
        try {
            return new AccountRef(AccountKind.valueOf(kindName), Long.parseLong(idText));
        } catch (IllegalArgumentException exception) {
            throw invalidPrincipalName(principalName);
        }
    }

    private static IllegalArgumentException invalidPrincipalName(String principalName) {
        return new IllegalArgumentException("주체 이름은 '종류:id' 모양이어야 합니다: " + principalName);
    }
}
