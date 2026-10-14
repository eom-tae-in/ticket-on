package com.ticketon.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("주최자 운영진 연락처 조회 — 주최자·소속 표가 생기기 전")
class OrganizerStaffContactPersistenceAdapterTest {

    @Test
    @DisplayName("주최자 표가 아직 없어 어떤 주최자도 찾지 못한다")
    void noOrganizerYet() {
        assertThat(new OrganizerStaffContactPersistenceAdapter().findStaffContacts(1)).isEmpty();
    }
}
