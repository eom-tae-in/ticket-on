package com.ticketon.domain.event;

import com.ticketon.domain.error.RuleViolationException;
import java.util.Objects;

/**
 * 행사의 공개 수준 설정 — 공개 수준과, 암호 보호일 때의 암호 해시(docs/03 "공개 수준 3단계").
 * <p>
 * <b>암호 보호면 암호 해시가 있고, 아니면 없다.</b> 암호 원문은 저장하지 않는다. 처음에는 공개다({@link #publicByDefault}).
 * <p>
 * 바꿀 때({@link #change}) 암호 보호로 두면서 새 암호를 적지 않으면, 이미 암호 보호라 걸려 있던 암호를 그대로 쓴다 — 주최자에게 저장된 암호를
 * 다시 보여 줄 수 없으므로, 다른 설정을 고치느라 같은 단계를 다시 저장할 때마다 암호를 새로 적게 하지 않는다. 암호가 걸린 적이 없는데 암호
 * 보호로 두면서 암호를 적지 않으면 막는다. 공개나 링크 전용으로 바꾸면 걸려 있던 암호는 버린다.
 *
 * @param visibility   공개 수준
 * @param passwordHash 암호 해시 — 암호 보호일 때만 있고, 아니면 null
 */
public record EventVisibilitySetting(EventVisibility visibility, EventPasswordHash passwordHash) {

    private static final EventVisibilitySetting PUBLIC = new EventVisibilitySetting(EventVisibility.PUBLIC, null);

    public EventVisibilitySetting {
        Objects.requireNonNull(visibility, "visibility");
        if (visibility.requiresPassword() != (passwordHash != null)) {
            throw new IllegalArgumentException("암호 보호일 때만 암호 해시를 가집니다: " + visibility);
        }
    }

    /** 처음 설정 — 공개. 새 행사와 복제한 행사가 이것으로 시작한다(docs/04 "행사 복제 — 공개 수준은 기본값 공개로 시작"). */
    public static EventVisibilitySetting publicByDefault() {
        return PUBLIC;
    }

    /**
     * 공개 수준을 바꾼 설정. 이 설정은 그대로 두고 새 설정을 돌려준다.
     * <ul>
     *   <li>암호 보호 — 새 암호 해시가 있으면 그것으로, 없으면 지금 걸려 있는 암호를 그대로 쓴다. 둘 다 없으면 막는다.</li>
     *   <li>공개 · 링크 전용 — 암호를 버린다. 새 암호 해시를 넘기지 않는다(해시는 암호 보호일 때만 만든다).</li>
     * </ul>
     *
     * @param newVisibility   바꿀 공개 수준
     * @param newPasswordHash 새로 정한 암호의 해시 — 암호 보호로 두면서 암호를 새로 적었을 때만. 아니면 null
     * @throws RuleViolationException   암호 보호로 두는데 새 암호도, 걸려 있던 암호도 없으면({@link EventErrorCode#EVENT_PASSWORD_REQUIRED})
     * @throws IllegalArgumentException 암호 보호가 아닌데 새 암호 해시를 넘기면
     */
    public EventVisibilitySetting change(EventVisibility newVisibility, EventPasswordHash newPasswordHash) {
        Objects.requireNonNull(newVisibility, "newVisibility");
        if (!newVisibility.requiresPassword()) {
            if (newPasswordHash != null) {
                throw new IllegalArgumentException("암호 보호가 아닌 공개 수준에는 암호를 걸지 않습니다: " + newVisibility);
            }
            return new EventVisibilitySetting(newVisibility, null);
        }
        EventPasswordHash hash = newPasswordHash != null ? newPasswordHash : passwordHash;
        if (hash == null) {
            throw new RuleViolationException(EventErrorCode.EVENT_PASSWORD_REQUIRED, "지금 공개 수준: " + visibility);
        }
        return new EventVisibilitySetting(EventVisibility.PASSWORD_PROTECTED, hash);
    }

    /** 목록 · 검색에 뜨는 공개 수준인지({@link EventVisibility#isListed}). */
    public boolean isListed() {
        return visibility.isListed();
    }

    /** 열려면 암호가 필요한지({@link EventVisibility#requiresPassword}). */
    public boolean requiresPassword() {
        return visibility.requiresPassword();
    }
}
