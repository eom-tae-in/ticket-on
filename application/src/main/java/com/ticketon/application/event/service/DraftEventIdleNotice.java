package com.ticketon.application.event.service;

import com.ticketon.application.event.query.IdleDraftEvent;
import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.policy.TimePolicy;
import java.time.LocalDate;

/**
 * 작성 중 행사 30일 미수정 메일 — 그 주최자의 운영진 전원에게 보낸다(docs/12 "주최자 대상 — 작성 중 행사 30일 미수정",
 * {@link NotificationKind#DRAFT_EVENT_IDLE}). 주최자 쪽 알림이라 이메일로만 가고 제목에 [주최]가 붙는다(알림이 붙인다).
 * <p>
 * 작성 중 목록 카드와 같은 것(진행률 · 마지막 수정일 · 이어서 작성)을 적어 멈춘 자리로 바로 돌아가게 한다(docs/04 "작성 중 행사 관리"). 여러
 * 주최자에 소속된 운영진이 어느 주최자의 행사인지 알게 주최자명을 적는다. 자동으로 지우지 않는다고 함께 적는다 — 지워질까 걱정해 서두르게 하지
 * 않는다(docs/99 B "작성 중 행사 자동 삭제"). 링크는 주최 화면({@value #ORGANIZER_PAGE_PATH})을 연다.
 */
final class DraftEventIdleNotice {

    /** 주최 화면의 시작 경로. 주최 화면은 /organizer 아래에 있다(web/src/app/organizer). */
    static final String ORGANIZER_PAGE_PATH = "/organizer";

    private DraftEventIdleNotice() {
    }

    static SendNotificationCommand commandFor(IdleDraftEvent draft, WebAddress webAddress) {
        return new SendNotificationCommand(NotificationRecipient.organizer(draft.organizerId()), NotificationKind.DRAFT_EVENT_IDLE,
                new NotificationContent(titleOf(draft), bodyOf(draft, webAddress)));
    }

    static String titleOf(IdleDraftEvent draft) {
        return "작성 중인 행사 '%s' — %d일 동안 수정이 없었습니다".formatted(draft.title(), idleDays());
    }

    private static String bodyOf(IdleDraftEvent draft, WebAddress webAddress) {
        LocalDate lastModifiedOn = KoreanTime.toDate(draft.lastModifiedAt());
        return """
                '%s'에서 작성 중인 행사 '%s'에 %d일 동안 수정이 없었습니다.
                마지막 수정: %d년 %d월 %d일
                진행: %d/%d 단계까지 저장
                이어서 작성하려면 주최 관리의 내 행사 목록에서 [이어서 작성]을 눌러 주세요.
                %s

                작성 중인 행사는 자동으로 지우지 않습니다. 이 알림은 수정이 없는 동안 한 번만 보냅니다.
                """.formatted(draft.organizerName(), draft.title(), idleDays(), lastModifiedOn.getYear(),
                lastModifiedOn.getMonthValue(), lastModifiedOn.getDayOfMonth(), draft.wizardProgress().number(),
                EventWizardStep.stepCount(), webAddress.page(ORGANIZER_PAGE_PATH));
    }

    private static int idleDays() {
        return TimePolicy.DRAFT_EVENT_IDLE_NOTICE.getDays();
    }
}
