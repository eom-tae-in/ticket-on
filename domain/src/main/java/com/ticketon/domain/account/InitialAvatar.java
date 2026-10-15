package com.ticketon.domain.account;

import com.ticketon.domain.policy.QuantityPolicy;
import java.text.Normalizer;
import java.util.Objects;

/**
 * 첫 글자 표시 — 이름의 첫 글자와, 대상마다 정해진 색(팔레트 번호).
 * <p>
 * 두 곳이 같은 표시를 쓴다(docs/01 "공개 페이지").
 * <ul>
 *   <li><b>로고가 없는 주최자</b> — 주최자명 첫 글자 + 주최자마다 정해진 색. 기본 그림은 겹치고 로고로 오해되지만, 이름에서 나오는 표시는
 *       구분된다(docs/99 D "주최자 로고").</li>
 *   <li><b>운영진 프로필</b> — 닉네임 첫 글자 + 사람마다 정해진 색. 사진은 받지 않는다 — 얼굴이 공개 페이지에 남지 않게.</li>
 * </ul>
 * 색은 {@value QuantityPolicy#INITIAL_AVATAR_COLOR_COUNT}가지 팔레트에서 대상의 id로 정해진다({@link #paletteIndexOf}). 같은 대상은 이름을
 * 바꿔도 색이 그대로다. <b>팔레트의 실제 색 값은 문서·디자인에 아직 없어</b> 도메인은 팔레트 번호(0부터)까지만 정하고, 번호를 색으로
 * 바꾸는 일은 화면이 한다.
 *
 * @param initial      첫 글자 — 앞뒤 공백을 걷고 NFC로 맞춘 이름의 첫 글자(한글 한 자, 이모지 하나가 한 글자)
 * @param paletteIndex 팔레트 번호. 0 이상 {@value QuantityPolicy#INITIAL_AVATAR_COLOR_COUNT} 미만.
 */
public record InitialAvatar(String initial, int paletteIndex) {

    public InitialAvatar {
        Objects.requireNonNull(initial, "initial");
        if (initial.isEmpty() || initial.codePointCount(0, initial.length()) != 1) {
            throw new IllegalArgumentException("첫 글자는 한 글자여야 합니다: " + initial);
        }
        if (paletteIndex < 0 || paletteIndex >= QuantityPolicy.INITIAL_AVATAR_COLOR_COUNT) {
            throw new IllegalArgumentException("팔레트 번호는 0 이상 " + QuantityPolicy.INITIAL_AVATAR_COLOR_COUNT
                    + " 미만이어야 합니다: " + paletteIndex);
        }
    }

    /**
     * 이름과 id로 만든다. 예: 주최자 "극단 열두달" → "극", 닉네임 "조용한고래" → "조".
     *
     * @param displayName 보이는 이름 — 주최자명이나 닉네임
     * @param id          대상의 id — 주최자 id나 운영진 계정 id. 색을 정한다.
     * @throws IllegalArgumentException 이름이 비었거나 id가 1보다 작으면
     */
    public static InitialAvatar of(String displayName, long id) {
        Objects.requireNonNull(displayName, "displayName");
        String normalized = Normalizer.normalize(displayName.strip(), Normalizer.Form.NFC);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("이름이 비어 있습니다");
        }
        String initial = new String(Character.toChars(normalized.codePointAt(0)));
        return new InitialAvatar(initial, paletteIndexOf(id));
    }

    /**
     * id로 정해지는 팔레트 번호 — id를 팔레트 가짓수로 나눈 나머지. 같은 id는 언제나 같은 번호이고, 이어진 id는 서로 다른 색을 받는다.
     *
     * @throws IllegalArgumentException id가 1보다 작으면
     */
    public static int paletteIndexOf(long id) {
        if (id < 1) {
            throw new IllegalArgumentException("id는 1 이상이어야 합니다: " + id);
        }
        return (int) (id % QuantityPolicy.INITIAL_AVATAR_COLOR_COUNT);
    }
}
