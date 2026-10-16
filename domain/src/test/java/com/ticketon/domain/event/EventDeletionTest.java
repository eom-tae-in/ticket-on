package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static com.ticketon.domain.event.EventStatusFixtures.springPlayIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("행사 삭제 — 상태가 아니라 팔린 표나 초대권이 있었는지로 정한다 (docs/04 \"행사 삭제\", docs/01 \"권한 두 가지\")")
class EventDeletionTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(EventStatus.class)
    @DisplayName("결제된 예매와 초대권이 한 번도 없었으면 상태와 상관없이 지울 수 있다")
    void deletableWithoutTickets(EventStatus status) {
        assertThatNoException().isThrownBy(() -> springPlayIn(status).requireDeletable(false));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(EventStatus.class)
    @DisplayName("한 장이라도 팔렸거나 초대권을 발권했으면(전부 취소 · 회수됐어도) 지울 수 없다")
    void notDeletableWithTicketHistory(EventStatus status) {
        assertRejected(() -> springPlayIn(status).requireDeletable(true), EventErrorCode.EVENT_HAD_TICKETS);
    }

    @Test
    @DisplayName("작성 중 행사는 운영진 누구나 지운다")
    void draftDeletedByAnyStaff() {
        StaffAction action = springPlayIn(EventStatus.DRAFT).deleteAction();

        assertThat(action).isEqualTo(StaffAction.DELETE_EVENT_BEFORE_SALES_STARTED);
        assertThat(StaffRole.OPERATOR.can(action)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = EventStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
    @DisplayName("판매를 시작한 뒤에는 소유자만 지운다")
    void startedEventDeletedByOwnerOnly(EventStatus status) {
        StaffAction action = springPlayIn(status).deleteAction();

        assertThat(action).isEqualTo(StaffAction.DELETE_EVENT_AFTER_SALES_STARTED);
        assertThat(StaffRole.OWNER.can(action)).isTrue();
        assertThat(StaffRole.OPERATOR.can(action)).isFalse();
    }

    @Test
    @DisplayName("가격을 잘못 넣고 판매를 시작했는데 아직 0건이면 소유자가 지우고 새로 만든다 (docs/15 박정훈)")
    void ownerDeletesOnSaleEventWithoutSales() {
        Event event = springPlayIn(EventStatus.ON_SALE);

        assertThatNoException().isThrownBy(() -> event.requireDeletable(false));
        assertThat(event.deleteAction()).isEqualTo(StaffAction.DELETE_EVENT_AFTER_SALES_STARTED);
    }
}
