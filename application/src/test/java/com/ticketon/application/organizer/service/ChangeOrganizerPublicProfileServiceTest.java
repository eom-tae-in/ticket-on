package com.ticketon.application.organizer.service;

import static com.ticketon.application.organizer.service.OrganizerStaffScene.DOHYUN;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.JUNGHOON;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.NOW;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.PLANNER;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.YEOREUM;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.acting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.organizer.port.in.ChangeOrganizerPublicProfileCommand;
import com.ticketon.application.organizer.port.in.GetOrganizerPublicProfileQuery;
import com.ticketon.application.organizer.port.in.OrganizerPublicProfileResult;
import com.ticketon.domain.account.InitialAvatar;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달의 운영진이 주최자 페이지의 소개 · SNS 링크를 고친다(docs/01 "공개 페이지 — 편집", {@link OrganizerStaffScene}). 운영진 누구나 고치고,
 * 심사용으로 낸 링크와 별개다.
 */
@DisplayName("주최자 공개 정보(소개 · SNS 링크) 편집 · 편집 화면 조회 유스케이스")
class ChangeOrganizerPublicProfileServiceTest {

    private static final String INTRODUCTION = "2019년부터 소극장 정기공연을 해마다 두 번 올립니다.";
    private static final List<ExternalLink> LINKS = List.of(
            ExternalLink.of("https://www.instagram.com/twelve_months_theater"),
            ExternalLink.of("https://blog.example.com/twelve-months"));

    private final OrganizerStaffScene scene = new OrganizerStaffScene();
    private final ChangeOrganizerPublicProfileService service = scene.changeOrganizerPublicProfileService();

    private OrganizerPublicProfileResult change(long staffAccountId, long organizerId, String introduction,
            List<ExternalLink> links) {
        return service.change(new ChangeOrganizerPublicProfileCommand(acting(staffAccountId, organizerId), introduction,
                links));
    }

    @Test
    @DisplayName("운영자도 고친다 — 소개는 앞뒤 공백을 걷고 링크는 적은 순서대로 저장되며, 로고가 없어 첫 글자 표시를 돌려준다")
    void operatorChanges() {
        OrganizerPublicProfileResult result = change(PLANNER, scene.twelveMonths, " " + INTRODUCTION + " ", LINKS);

        assertThat(result.organizerId()).isEqualTo(scene.twelveMonths);
        assertThat(result.introduction()).isEqualTo(INTRODUCTION);
        assertThat(result.snsLinks()).containsExactly("https://www.instagram.com/twelve_months_theater",
                "https://blog.example.com/twelve-months");
        assertThat(result.logoUrl()).isNull();
        assertThat(result.initial()).isEqualTo("극");
        assertThat(result.paletteIndex()).isEqualTo(InitialAvatar.paletteIndexOf(scene.twelveMonths));
        assertThat(scene.organizerRepository.publicProfileSaves()).isEqualTo(1);
        assertThat(scene.organizerRepository.findById(scene.twelveMonths).orElseThrow().publicProfile().snsLinks())
                .containsExactlyElementsOf(LINKS);
    }

    @Test
    @DisplayName("고친 운영진의 조작 기록이 주최자를 대상으로 남는다")
    void recordsOperation() {
        change(PLANNER, scene.twelveMonths, INTRODUCTION, LINKS);

        assertThat(scene.recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.organizerId()).isEqualTo(scene.twelveMonths);
            assertThat(record.staffAccountId()).isEqualTo(PLANNER);
            assertThat(record.staffRole()).isEqualTo(StaffRole.OPERATOR);
            assertThat(record.action()).isEqualTo(StaffAction.EDIT_PUBLIC_PROFILE);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.ORGANIZER);
            assertThat(record.targetId()).isEqualTo(scene.twelveMonths);
            assertThat(record.summary()).isEqualTo("공개 정보 수정 (소개 · SNS 링크)");
            assertThat(record.operatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    @DisplayName("잠금 순서 — 고치는 운영진의 계정 줄에 외래 키 확인 잠금을 주최자 줄보다 먼저 건다 (계정 줄 → 주최자 줄, #195)")
    void locksEditorAccountBeforeOrganizer() {
        change(PLANNER, scene.twelveMonths, INTRODUCTION, LINKS);

        assertThat(scene.rowLockLog.locks()).containsExactly("계정 " + PLANNER + " FOR KEY SHARE",
                "주최자 " + scene.twelveMonths + " FOR UPDATE");
        assertThat(scene.staffAccountRepository.lockedAccountIds()).isEmpty();
    }

    @Test
    @DisplayName("소개를 비우고 링크를 빼면 지운다")
    void clears() {
        change(JUNGHOON, scene.twelveMonths, INTRODUCTION, LINKS);

        OrganizerPublicProfileResult result = change(JUNGHOON, scene.twelveMonths, "　", null);

        assertThat(result.introduction()).isNull();
        assertThat(result.snsLinks()).isEmpty();
        assertThat(scene.recordRepository.records()).hasSize(2);
    }

    @Test
    @DisplayName("지금과 같은 값이면 저장도 조작 기록도 하지 않는다")
    void sameValuesLeaveNoRecord() {
        change(JUNGHOON, scene.twelveMonths, INTRODUCTION, LINKS);

        OrganizerPublicProfileResult result = change(PLANNER, scene.twelveMonths, INTRODUCTION + " ", LINKS);

        assertThat(result.introduction()).isEqualTo(INTRODUCTION);
        assertThat(scene.organizerRepository.publicProfileSaves()).isEqualTo(1);
        assertThat(scene.recordRepository.records()).hasSize(1);
    }

    @Test
    @DisplayName("그 주최자의 운영진이 아니면 고치지 못한다 — 다른 주최자의 소유자 · 운영자도")
    void outsiderIsRejected() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> change(DOHYUN, scene.twelveMonths, INTRODUCTION, LINKS))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> change(YEOREUM, scene.twelveMonths, INTRODUCTION, LINKS));
        assertThat(scene.organizerRepository.publicProfileSaves()).isZero();
        assertThat(scene.recordRepository.records()).isEmpty();
        assertThat(scene.rowLockLog.locks()).isEmpty();
    }

    @Test
    @DisplayName("편집 화면은 운영진 누구나 지금 공개 정보로 채우고, 보기만 해서 조작 기록이 없다")
    void editScreenShowsCurrentProfile() {
        change(JUNGHOON, scene.twelveMonths, INTRODUCTION, LINKS);
        GetOrganizerPublicProfileService getService = scene.getOrganizerPublicProfileService();

        OrganizerPublicProfileResult shown = getService.get(
                new GetOrganizerPublicProfileQuery(acting(PLANNER, scene.twelveMonths)));

        assertThat(shown.introduction()).isEqualTo(INTRODUCTION);
        assertThat(shown.snsLinks()).hasSize(2);
        assertThat(shown.hasLogo()).isFalse();
        assertThat(scene.recordRepository.records()).hasSize(1);
        assertThatExceptionOfType(PermissionDeniedException.class).isThrownBy(() -> getService.get(
                new GetOrganizerPublicProfileQuery(acting(DOHYUN, scene.twelveMonths))));
    }
}
