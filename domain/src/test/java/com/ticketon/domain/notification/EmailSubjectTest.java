package com.ticketon.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("이메일 제목 접두사 규칙")
class EmailSubjectTest {

    @Nested
    @DisplayName("거래 알림")
    class Notice {

        @Test
        @DisplayName("관객 쪽 메일은 접두사 없이 제목 그대로다")
        void audienceHasNoPrefix() {
            EmailSubject subject = EmailSubject.notice(RecipientSide.AUDIENCE, "예매가 완료되었습니다");

            assertThat(subject.value()).isEqualTo("예매가 완료되었습니다");
        }

        @Test
        @DisplayName("주최자 쪽 메일은 [주최]로 시작한다")
        void organizerHasOrganizerPrefix() {
            EmailSubject subject = EmailSubject.notice(RecipientSide.ORGANIZER, "주최자 신청이 승인되었습니다");

            assertThat(subject.value()).isEqualTo("[주최] 주최자 신청이 승인되었습니다");
        }

        @Test
        @DisplayName("같은 제목이라도 받는 쪽이 다르면 메일함에서 구분된다")
        void sameTitleDiffersBySide() {
            EmailSubject toAudience = EmailSubject.notice(RecipientSide.AUDIENCE, "회차가 취소되었습니다");
            EmailSubject toOrganizer = EmailSubject.notice(RecipientSide.ORGANIZER, "회차가 취소되었습니다");

            assertThat(toAudience).isNotEqualTo(toOrganizer);
            assertThat(toAudience.value()).doesNotStartWith("[주최]");
            assertThat(toOrganizer.value()).startsWith("[주최]");
        }

        @Test
        @DisplayName("받는 쪽이 없으면 거부한다")
        void missingSideRejected() {
            assertThatThrownBy(() -> EmailSubject.notice(null, "예매가 완료되었습니다"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("광고 메일")
    class Advertisement {

        @Test
        @DisplayName("제목 맨 앞이 (광고)다")
        void startsWithAdvertisementMark() {
            EmailSubject subject = EmailSubject.advertisement("이번 주 새로 열린 공연");

            assertThat(subject.value()).startsWith("(광고)");
            assertThat(subject.value()).isEqualTo("(광고) 이번 주 새로 열린 공연");
        }

        @Test
        @DisplayName("[주최] 접두사는 붙지 않는다")
        void hasNoOrganizerPrefix() {
            EmailSubject subject = EmailSubject.advertisement("이번 주 새로 열린 공연");

            assertThat(subject.value()).doesNotContain("[주최]");
        }
    }

    @Test
    @DisplayName("접두사를 뺀 제목이 비어 있으면 거부한다")
    void blankTitleRejected() {
        assertThatThrownBy(() -> EmailSubject.notice(RecipientSide.AUDIENCE, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmailSubject.notice(RecipientSide.ORGANIZER, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmailSubject.advertisement(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmailSubject.advertisement(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("보낼 제목이 같으면 같은 값이고, 문자열은 보낼 제목 그대로다")
    void valueEquality() {
        EmailSubject subject = EmailSubject.notice(RecipientSide.ORGANIZER, "정산이 완료되었습니다");

        assertThat(subject).isEqualTo(EmailSubject.notice(RecipientSide.ORGANIZER, "정산이 완료되었습니다"));
        assertThat(subject).hasSameHashCodeAs(EmailSubject.notice(RecipientSide.ORGANIZER, "정산이 완료되었습니다"));
        assertThat(subject).hasToString("[주최] 정산이 완료되었습니다");
    }
}
