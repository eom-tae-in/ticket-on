package com.ticketon.api.account;

import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 카카오·구글 로그인이 세션에 두는 것 — 연결 의도, 로그인 시작 정보, '추가 정보 입력 대기', 마지막 결과.
 * <p>
 * 세션은 DB(spring_session_attributes)에 직렬화돼 저장된다. 그래서 글자와 이 패키지의 enum만 담고 도메인 객체를 넣지 않는다 — 도메인 클래스를
 * 바꿔도 이미 열린 세션을 읽을 수 있게(AccountAuthentication와 같은 까닭). 로그아웃하면 세션과 함께 사라진다.
 * <ul>
 *   <li>연결 의도({@link LinkIntent}) — 마이페이지에서 연결을 시작한 관객과 제공자. 다음 로그인 시작이 가져간다.</li>
 *   <li>시작 정보({@link Start}) — 로그인인지 연결인지, 돌아갈 경로. 제공자에서 돌아오면 가져간다.</li>
 *   <li>추가 정보 입력 대기({@link PendingSignUp}) — 연결된 관객 계정이 없는 제공자 계정. 가입 API가 이것으로 가입한다.</li>
 *   <li>마지막 결과({@link Result}) — 화면이 결과 API로 읽는다. 다음 로그인을 시작하면 지워진다.</li>
 * </ul>
 */
final class AudienceOAuthSession {

    private static final String PREFIX = AudienceOAuthSession.class.getName() + ".";

    private static final String LINK_INTENT = PREFIX + "LINK_INTENT";

    private static final String START = PREFIX + "START";

    private static final String PENDING_SIGN_UP = PREFIX + "PENDING_SIGN_UP";

    private static final String RESULT = PREFIX + "RESULT";

    private AudienceOAuthSession() {
    }

    static void saveLinkIntent(HttpServletRequest request, LinkIntent linkIntent) {
        request.getSession().setAttribute(LINK_INTENT, linkIntent);
    }

    /** 연결 의도를 꺼내고 세션에서 지운다. */
    static Optional<LinkIntent> takeLinkIntent(HttpServletRequest request) {
        return take(request, LINK_INTENT, LinkIntent.class);
    }

    /** 새 로그인을 시작한다 — 시작 정보를 두고, 이전 로그인의 결과와 추가 정보 입력 대기를 지운다. */
    static void start(HttpServletRequest request, Start start) {
        HttpSession session = request.getSession();
        session.removeAttribute(RESULT);
        session.removeAttribute(PENDING_SIGN_UP);
        session.setAttribute(START, start);
    }

    /** 시작 정보를 꺼내고 세션에서 지운다. */
    static Optional<Start> takeStart(HttpServletRequest request) {
        return take(request, START, Start.class);
    }

    static void savePendingSignUp(HttpServletRequest request, PendingSignUp pendingSignUp) {
        request.getSession().setAttribute(PENDING_SIGN_UP, pendingSignUp);
    }

    static Optional<PendingSignUp> pendingSignUp(HttpServletRequest request) {
        return read(request, PENDING_SIGN_UP, PendingSignUp.class);
    }

    /** 가입을 마쳤다 — 추가 정보 입력 대기와 그 로그인의 결과를 지운다. 같은 대기로 두 번 가입하지 않게. */
    static void finishSignUp(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(PENDING_SIGN_UP);
            session.removeAttribute(RESULT);
        }
    }

    static void saveResult(HttpServletRequest request, Result result) {
        request.getSession().setAttribute(RESULT, result);
    }

    static Optional<Result> result(HttpServletRequest request) {
        return read(request, RESULT, Result.class);
    }

    private static <T> Optional<T> read(HttpServletRequest request, String name, Class<T> type) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(session.getAttribute(name)).filter(type::isInstance).map(type::cast);
    }

    private static <T> Optional<T> take(HttpServletRequest request, String name, Class<T> type) {
        Optional<T> value = read(request, name, type);
        value.ifPresent(present -> request.getSession().removeAttribute(name));
        return value;
    }

    /** 로그인 시작의 목적. */
    enum Purpose {

        /** 카카오·구글로 로그인(연결된 계정이 없으면 가입). */
        LOGIN,

        /** 로그인한 관객의 계정에 연결. */
        LINK
    }

    /**
     * 마이페이지에서 연결을 시작한 관객과 제공자.
     *
     * @param provider      연결할 제공자
     * @param principalName 연결을 시작한 로그인 주체 이름("AUDIENCE:id") — 돌아왔을 때도 같은 주체로 로그인해 있어야 연결한다
     * @param returnPath    연결을 마치고 돌아갈 화면 경로
     */
    record LinkIntent(OAuthProviderCode provider, String principalName, String returnPath) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        LinkIntent {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(principalName, "principalName");
            Objects.requireNonNull(returnPath, "returnPath");
        }

        static LinkIntent of(OAuthProviderCode provider, AccountRef account, String returnPath) {
            return new LinkIntent(provider, account.principalName(), returnPath);
        }
    }

    /**
     * 로그인 시작 정보.
     *
     * @param purpose              로그인인지 연결인지
     * @param provider             제공자
     * @param returnPath           마치고 돌아갈 화면 경로 — 같은 사이트 안의 경로(ReturnPath)
     * @param linkingPrincipalName 연결이면 연결을 시작한 로그인 주체 이름. 로그인이면 null.
     */
    record Start(Purpose purpose, OAuthProviderCode provider, String returnPath, @Nullable String linkingPrincipalName)
            implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        Start {
            Objects.requireNonNull(purpose, "purpose");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(returnPath, "returnPath");
            if ((purpose == Purpose.LINK) != (linkingPrincipalName != null)) {
                throw new IllegalArgumentException("연결일 때만 연결을 시작한 주체가 있습니다");
            }
        }

        static Start logIn(OAuthProviderCode provider, String returnPath) {
            return new Start(Purpose.LOGIN, provider, returnPath, null);
        }

        static Start link(LinkIntent linkIntent) {
            return new Start(Purpose.LINK, linkIntent.provider(), linkIntent.returnPath(), linkIntent.principalName());
        }
    }

    /**
     * 추가 정보 입력 대기 — 연결된 관객 계정이 없는 제공자 계정과 제공자가 알려 준 값.
     *
     * @param provider       제공자
     * @param providerUserId 제공자 사용자 id
     * @param name           제공자가 알려 준 이름(추가 정보 입력을 미리 채움). 없으면 null.
     * @param email          제공자가 알려 준 이메일. 없으면 null.
     * @param emailVerified  그 이메일을 제공자가 확인했는지 — 참이면 이 이메일로 가입하고 이메일 인증을 거친 것으로 본다
     * @param returnPath     가입하고 로그인한 뒤 돌아갈 화면 경로
     */
    record PendingSignUp(OAuthProviderCode provider, String providerUserId, @Nullable String name, @Nullable String email,
            boolean emailVerified, String returnPath) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        PendingSignUp {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(providerUserId, "providerUserId");
            Objects.requireNonNull(returnPath, "returnPath");
            if (email == null && emailVerified) {
                throw new IllegalArgumentException("이메일 없이 확인된 이메일일 수 없습니다");
            }
        }

        static PendingSignUp of(AudienceOAuthProfile profile, String returnPath) {
            return new PendingSignUp(profile.provider(), profile.identity().providerUserId(), profile.name(),
                    profile.email() == null ? null : profile.email().value(), profile.emailVerified(), returnPath);
        }
    }

    /**
     * 카카오·구글 로그인 한 번의 결과.
     *
     * @param outcome      결과
     * @param provider     제공자. 시작 정보 없이 돌아온 실패(세션이 끝났거나 시작하지 않은 콜백)면 null.
     * @param returnPath   화면이 이어서 갈 경로
     * @param errorCode    실패면 오류 코드. 아니면 null.
     * @param errorMessage 실패면 화면에 보여 줄 문구. 아니면 null.
     */
    record Result(OAuthLoginOutcome outcome, @Nullable OAuthProviderCode provider, String returnPath,
            @Nullable String errorCode, @Nullable String errorMessage) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        Result {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(returnPath, "returnPath");
            if ((outcome == OAuthLoginOutcome.FAILED) != (errorCode != null && errorMessage != null)) {
                throw new IllegalArgumentException("실패일 때만 오류 코드와 문구가 있습니다");
            }
            if (outcome != OAuthLoginOutcome.FAILED && provider == null) {
                throw new IllegalArgumentException("성공한 결과에는 제공자가 있어야 합니다");
            }
        }

        static Result succeeded(OAuthLoginOutcome outcome, OAuthProviderCode provider, String returnPath) {
            Objects.requireNonNull(provider, "provider");
            return new Result(outcome, provider, returnPath, null, null);
        }

        static Result failed(@Nullable OAuthProviderCode provider, String returnPath, ErrorCode error) {
            return new Result(OAuthLoginOutcome.FAILED, provider, returnPath, error.code(), error.message());
        }
    }
}
