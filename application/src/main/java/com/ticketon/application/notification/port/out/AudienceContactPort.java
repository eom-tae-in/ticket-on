package com.ticketon.application.notification.port.out;

import com.ticketon.domain.notification.ContactPoint;
import java.util.Optional;

/**
 * 관객 계정의 연락처 조회 — 나가는 포트. 알림을 관객 계정으로 보낼 때 받는 곳(가입 때 받은 이메일·전화번호)을 찾는다.
 */
public interface AudienceContactPort {

    /**
     * 그 계정의 이메일·전화번호.
     * <p>
     * 탈퇴한 계정이나 없는 계정이면 비어 있다. 탈퇴하면 연락처를 쓰지 않는다 — 법정 보존 대상 외에는 즉시 파기한다(docs/11 "데이터 보관").
     * 인증 대기·이용 정지 계정은 연락처가 있다.
     */
    Optional<ContactPoint> findContact(long audienceAccountId);
}
