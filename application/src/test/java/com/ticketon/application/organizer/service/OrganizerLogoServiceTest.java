package com.ticketon.application.organizer.service;

import static com.ticketon.application.organizer.service.OrganizerStaffScene.DOHYUN;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.JUNGHOON;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.NOW;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.PLANNER;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.acting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.application.organizer.port.in.ChangeOrganizerPublicProfileCommand;
import com.ticketon.application.organizer.port.in.GetOrganizerLogoFileQuery;
import com.ticketon.application.organizer.port.in.OrganizerLogoFileResult;
import com.ticketon.application.organizer.port.in.OrganizerPublicProfileResult;
import com.ticketon.application.organizer.port.in.RemoveOrganizerLogoCommand;
import com.ticketon.application.organizer.port.in.UploadOrganizerLogoCommand;
import com.ticketon.common.FixedTokenGenerator;
import com.ticketon.common.TestImages;
import com.ticketon.domain.account.InitialAvatar;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerLogo;
import com.ticketon.domain.organizer.OrganizerLogoImage;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 극단 열두달의 운영진이 로고를 올리고 바꾸고 지운다(docs/01 "공개 페이지 — 로고", {@link OrganizerStaffScene}). 파일은 추측할 수 없는 열쇠로
 * 저장되고, 커밋된 뒤에 바꾸기 전 · 지운 로고의 파일을 지우며, 되돌려지면 새 파일을 지운다.
 */
@DisplayName("주최자 로고 올리기 · 지우기 · 파일 내주기 유스케이스")
class OrganizerLogoServiceTest {

    private static final String FIRST_TOKEN = "firstLogoTokenAAAAAAAAAAAAAAAAAA";
    private static final String SECOND_TOKEN = "secondLogoTokenBBBBBBBBBBBBBBBBB";

    private final OrganizerStaffScene scene = new OrganizerStaffScene();
    private final UploadOrganizerLogoService uploadService =
            scene.uploadOrganizerLogoService(new FixedTokenGenerator(FIRST_TOKEN, SECOND_TOKEN));
    private final RemoveOrganizerLogoService removeService = scene.removeOrganizerLogoService();

    private static OrganizerLogoImage squarePng() {
        return OrganizerLogoImage.of("image/png", TestImages.png(256, 256));
    }

    private OrganizerPublicProfileResult upload(long staffAccountId, OrganizerLogoImage image) {
        return SimulatedTransaction.run(() -> uploadService.upload(
                new UploadOrganizerLogoCommand(acting(staffAccountId, scene.twelveMonths), image)));
    }

    private OrganizerPublicProfileResult remove(long staffAccountId) {
        return SimulatedTransaction.run(() -> removeService.remove(
                new RemoveOrganizerLogoCommand(acting(staffAccountId, scene.twelveMonths))));
    }

    @Nested
    @DisplayName("올리기")
    class Upload {

        @Test
        @DisplayName("운영자가 올리면 추측할 수 없는 열쇠 + 실제 형식의 확장자로 파일이 저장되고, 공개 주소를 돌려준다")
        void operatorUploads() {
            OrganizerPublicProfileResult result = upload(PLANNER, squarePng());

            String fileKey = FIRST_TOKEN + ".png";
            assertThat(scene.logoStorage.storedKeys()).containsExactly(fileKey);
            assertThat(scene.logoStorage.read(new OrganizerLogo(fileKey)))
                    .hasValueSatisfying(content -> assertThat(content).isEqualTo(squarePng().content()));
            assertThat(result.logoUrl()).isEqualTo(FakeOrganizerLogoStorage.PUBLIC_BASE_URL + "/" + fileKey);
            assertThat(scene.organizerRepository.findById(scene.twelveMonths).orElseThrow().publicProfile().logo())
                    .isEqualTo(new OrganizerLogo(fileKey));
        }

        @Test
        @DisplayName("JPEG는 jpg 확장자로 저장한다")
        void jpegKeepsJpgExtension() {
            OrganizerPublicProfileResult result = upload(JUNGHOON,
                    OrganizerLogoImage.of("image/jpeg", TestImages.jpeg(300, 300)));

            assertThat(scene.logoStorage.storedKeys()).containsExactly(FIRST_TOKEN + ".jpg");
            assertThat(result.logoUrl()).endsWith(FIRST_TOKEN + ".jpg");
        }

        @Test
        @DisplayName("올린 운영진의 조작 기록이 주최자를 대상으로 남는다")
        void recordsOperation() {
            upload(PLANNER, squarePng());

            assertThat(scene.recordRepository.records()).singleElement().satisfies(record -> {
                assertThat(record.staffAccountId()).isEqualTo(PLANNER);
                assertThat(record.action()).isEqualTo(StaffAction.EDIT_PUBLIC_PROFILE);
                assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.ORGANIZER);
                assertThat(record.targetId()).isEqualTo(scene.twelveMonths);
                assertThat(record.summary()).isEqualTo("로고 올리기");
                assertThat(record.operatedAt()).isEqualTo(NOW);
            });
        }

        @Test
        @DisplayName("다시 올리면 새 열쇠로 저장하고, 커밋된 뒤 바꾸기 전 로고의 파일을 지운다 — 소개 · 링크는 그대로다")
        void replacingDeletesPreviousFileAfterCommit() {
            scene.changeOrganizerPublicProfileService().change(new ChangeOrganizerPublicProfileCommand(
                    acting(JUNGHOON, scene.twelveMonths), "소극장 극단",
                    List.of(ExternalLink.of("https://www.instagram.com/twelve_months_theater"))));
            upload(JUNGHOON, squarePng());

            OrganizerPublicProfileResult result = upload(PLANNER, squarePng());

            assertThat(scene.logoStorage.storedKeys()).containsExactly(SECOND_TOKEN + ".png");
            assertThat(result.logoUrl()).endsWith(SECOND_TOKEN + ".png");
            assertThat(result.introduction()).isEqualTo("소극장 극단");
            assertThat(result.snsLinks()).hasSize(1);
        }

        @Test
        @DisplayName("주최자 저장에서 되돌려지면 새로 올린 파일을 지우고, 바꾸기 전 로고와 그 파일은 그대로다")
        void rollbackDeletesNewFile() {
            upload(JUNGHOON, squarePng());
            scene.organizerRepository.failPublicProfileSaves();

            assertThatIllegalStateException().isThrownBy(() -> upload(PLANNER, squarePng()));

            assertThat(scene.logoStorage.storedKeys()).containsExactly(FIRST_TOKEN + ".png");
            assertThat(scene.recordRepository.records()).hasSize(1);
        }

        @Test
        @DisplayName("그 주최자의 운영진이 아니면 올리지 못하고 파일도 저장되지 않는다")
        void outsiderIsRejected() {
            assertThatExceptionOfType(PermissionDeniedException.class)
                    .isThrownBy(() -> upload(DOHYUN, squarePng()))
                    .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
            assertThat(scene.logoStorage.storedKeys()).isEmpty();
            assertThat(scene.recordRepository.records()).isEmpty();
        }
    }

    @Nested
    @DisplayName("지우기")
    class Remove {

        @Test
        @DisplayName("지우면 로고 주소가 비고 이름 첫 글자 + 정해진 색이 돌아오며, 커밋된 뒤 파일을 지운다")
        void removingFallsBackToInitialAvatar() {
            upload(JUNGHOON, squarePng());

            OrganizerPublicProfileResult result = remove(PLANNER);

            assertThat(result.logoUrl()).isNull();
            assertThat(result.hasLogo()).isFalse();
            assertThat(result.initial()).isEqualTo("극");
            assertThat(result.paletteIndex()).isEqualTo(InitialAvatar.paletteIndexOf(scene.twelveMonths));
            assertThat(scene.logoStorage.storedKeys()).isEmpty();
            assertThat(scene.organizerRepository.findById(scene.twelveMonths).orElseThrow().publicProfile().hasLogo())
                    .isFalse();
        }

        @Test
        @DisplayName("지운 운영진의 조작 기록이 남는다")
        void recordsOperation() {
            upload(JUNGHOON, squarePng());

            remove(PLANNER);

            assertThat(scene.recordRepository.records()).last().satisfies(record -> {
                assertThat(record.staffAccountId()).isEqualTo(PLANNER);
                assertThat(record.action()).isEqualTo(StaffAction.EDIT_PUBLIC_PROFILE);
                assertThat(record.summary()).isEqualTo("로고 삭제");
            });
        }

        @Test
        @DisplayName("로고가 없으면 저장도 조작 기록도 하지 않는다")
        void absentLogoLeavesNoRecord() {
            OrganizerPublicProfileResult result = remove(PLANNER);

            assertThat(result.hasLogo()).isFalse();
            assertThat(scene.organizerRepository.publicProfileSaves()).isZero();
            assertThat(scene.recordRepository.records()).isEmpty();
        }

        @Test
        @DisplayName("주최자 저장에서 되돌려지면 파일을 지우지 않는다 — 주최자는 그 파일을 계속 가리킨다")
        void rollbackKeepsFile() {
            upload(JUNGHOON, squarePng());
            scene.organizerRepository.failPublicProfileSaves();

            assertThatIllegalStateException().isThrownBy(() -> remove(PLANNER));

            assertThat(scene.logoStorage.storedKeys()).containsExactly(FIRST_TOKEN + ".png");
        }

        @Test
        @DisplayName("그 주최자의 운영진이 아니면 지우지 못한다")
        void outsiderIsRejected() {
            upload(JUNGHOON, squarePng());

            assertThatExceptionOfType(PermissionDeniedException.class).isThrownBy(() -> remove(DOHYUN));
            assertThat(scene.logoStorage.storedKeys()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("파일 내주기")
    class ServeFile {

        private final GetOrganizerLogoFileService fileService = new GetOrganizerLogoFileService(scene.logoStorage);

        @Test
        @DisplayName("저장된 로고 파일을 열쇠의 확장자로 정한 형식과 함께 내준다")
        void servesStoredFile() {
            upload(JUNGHOON, squarePng());

            OrganizerLogoFileResult file = fileService.get(new GetOrganizerLogoFileQuery(FIRST_TOKEN + ".png"));

            assertThat(file.content()).isEqualTo(squarePng().content());
            assertThat(file.mediaType()).isEqualTo("image/png");
        }

        @Test
        @DisplayName("지운 로고의 파일은 없다")
        void removedLogoIsNotFound() {
            upload(JUNGHOON, squarePng());
            remove(JUNGHOON);

            assertNotFound(FIRST_TOKEN + ".png");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @DisplayName("로고 열쇠의 꼴이 아니면 저장소에 묻지 않고 없다고 답한다")
        @ValueSource(strings = {"../../etc/passwd", "firstLogoTokenAAAAAAAAAAAAAAAAAA", "logo.png", "",
                "firstLogoTokenAAAAAAAAAAAAAAAAAA.svg"})
        void malformedKeyIsNotFound(String fileKey) {
            assertNotFound(fileKey);
        }

        private void assertNotFound(String fileKey) {
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> fileService.get(new GetOrganizerLogoFileQuery(fileKey)))
                    .satisfies(notFound -> assertThat(notFound.errorCode())
                            .isEqualTo(OrganizerErrorCode.ORGANIZER_LOGO_NOT_FOUND));
        }
    }
}
