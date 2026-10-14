package com.ticketon.persistence.notification;

import com.ticketon.application.notification.port.out.OrganizerStaffContactPort;
import com.ticketon.domain.notification.OrganizerStaffContacts;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 주최자 운영진 연락처 조회 어댑터.
 * <p>
 * <b>아직 주최자·소속 표가 없다.</b> 운영진 로그인의 이메일은 운영진 로그인 표(staff_account — V5, #15)에 있지만, 주최자와
 * 소속(소유자·운영자)은 주최자 도메인(#19)·신청과 심사(#20·#23)·운영자 초대(#25)가 표와 함께 만든다. 주최자 표가 없으니 어떤 주최자도
 * 없고, 그래서 늘 비어 있다 — 주최자 id로 알림을 보내면 유스케이스가 '없는 주최자'로 거부한다. 소속 표가 생기는 이슈에서 이 어댑터를
 * 소속 표와 staff_account의 이메일을 잇는 조회로 채운다.
 */
@Component
public class OrganizerStaffContactPersistenceAdapter implements OrganizerStaffContactPort {

    @Override
    public Optional<OrganizerStaffContacts> findStaffContacts(long organizerId) {
        return Optional.empty();
    }
}
