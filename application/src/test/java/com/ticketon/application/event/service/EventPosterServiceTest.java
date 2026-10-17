package com.ticketon.application.event.service;

import static com.ticketon.application.event.service.EventWizardScene.DOHYUN;
import static com.ticketon.application.event.service.EventWizardScene.JUNGHOON;
import static com.ticketon.application.event.service.EventWizardScene.NOW;
import static com.ticketon.application.event.service.EventWizardScene.PLANNER;
import static com.ticketon.application.event.service.EventWizardScene.YEOREUM;
import static com.ticketon.application.event.service.EventWizardScene.acting;
import static com.ticketon.application.event.service.EventWizardScene.springPlayItems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.EventPosterFileResult;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.event.port.in.GetEventPosterFileQuery;
import com.ticketon.application.event.port.in.GetEventWizardQuery;
import com.ticketon.application.event.port.in.UploadEventPosterCommand;
import com.ticketon.common.FixedTokenGenerator;
import com.ticketon.common.TestImages;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.event.EventPosterImage;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 극단 열두달의 운영진이 봄 정기공연의 포스터를 올리고 바꾼다(docs/03 "기본 정보" — 3:4 세로, {@link EventWizardScene}). 파일은 추측할 수 없는
 * 열쇠로 저장되고, 커밋된 뒤에 바꾸기 전 포스터의 파일을 지우며, 되돌려지면 새 파일을 지운다. 막힌 요청은 파일을 남기지 않는다.
 */
@DisplayName("행사 포스터 올리기 · 파일 내주기 유스케이스")
class EventPosterServiceTest {

    private static final String FIRST_TOKEN = "firstPosterTokenAAAAAAAAAAAAAAAA";
    private static final String SECOND_TOKEN = "secondPosterTokenBBBBBBBBBBBBBBB";
    private static final Instant LATER = NOW.plus(Duration.ofHours(2));

    private final EventWizardScene scene = new EventWizardScene();
    private final UploadEventPosterService uploadService =
            scene.uploadEventPosterService(new FixedTokenGenerator(FIRST_TOKEN, SECOND_TOKEN));
    private final long springPlay = scene.createEventService()
            .create(new CreateEventCommand(acting(JUNGHOON, scene.twelveMonths), springPlayItems())).eventId();

    /** 크롭 도구로 3:4로 자른 JPEG 포스터. */
    private static EventPosterImage threeByFourJpeg() {
        return EventPosterImage.of("image/jpeg", TestImages.jpeg(300, 400));
    }

    private EventWizardResult upload(long staffAccountId, long organizerId, long eventId, EventPosterImage image) {
        return SimulatedTransaction.run(() -> uploadService.upload(
                new UploadEventPosterCommand(acting(staffAccountId, organizerId), eventId, image)));
    }

    private EventWizardResult upload(long staffAccountId, EventPosterImage image) {
        return upload(staffAccountId, scene.twelveMonths, springPlay, image);
    }

    /** 봄 정기공연을 그 상태로 저장돼 있던 것으로 둔다 — 지금 포스터는 그대로다. */
    private void storeSpringPlayIn(EventStatus status) {
        Event stored = scene.eventRepository.findById(springPlay).orElseThrow();
        scene.eventRepository.store(Event.restore(springPlay, stored.organizerId(), stored.publicAddress(),
                stored.basicInformation(), stored.poster().orElse(null), stored.visibility(), stored.bookingSettings(),
                stored.cancellationPolicy(), status, status == EventStatus.AWAITING_OPEN ? null : NOW, stored.salesPauses(),
                EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS, NOW));
    }

    @Nested
    @DisplayName("올리기")
    class Upload {

        @Test
        @DisplayName("운영자가 처음 올리면 추측할 수 없는 열쇠 + 실제 형식의 확장자로 저장되고, 위저드 값에 공개 주소가 담긴다")
        void operatorUploads() {
            scene.clock.moveTo(LATER);

            EventWizardResult result = upload(PLANNER, threeByFourJpeg());

            String fileKey = FIRST_TOKEN + ".jpg";
            assertThat(scene.posterStorage.storedKeys()).containsExactly(fileKey);
            assertThat(scene.posterStorage.read(new EventPoster(fileKey)))
                    .hasValueSatisfying(content -> assertThat(content).isEqualTo(threeByFourJpeg().content()));
            assertThat(result.posterUrl()).isEqualTo(FakeEventPosterStorage.PUBLIC_BASE_URL + "/" + fileKey);
            assertThat(result.lastModifiedAt()).isEqualTo(LATER);
            assertThat(result.wizardProgress()).isEqualTo(EventWizardStep.BASIC_INFORMATION);
            Event stored = scene.eventRepository.findById(springPlay).orElseThrow();
            assertThat(stored.poster()).contains(new EventPoster(fileKey));
            assertThat(stored.isBasicInformationComplete()).isTrue();
        }

        @Test
        @DisplayName("PNG는 png 확장자로 저장한다")
        void pngKeepsPngExtension() {
            EventWizardResult result = upload(JUNGHOON, EventPosterImage.of("image/png", TestImages.png(900, 1200)));

            assertThat(scene.posterStorage.storedKeys()).containsExactly(FIRST_TOKEN + ".png");
            assertThat(result.posterUrl()).endsWith(FIRST_TOKEN + ".png");
        }

        @Test
        @DisplayName("올린 운영진의 조작 기록이 행사를 대상으로 남는다")
        void recordsOperation() {
            scene.clock.moveTo(LATER);

            upload(PLANNER, threeByFourJpeg());

            assertThat(scene.recordRepository.records()).last().satisfies(record -> {
                assertThat(record.staffAccountId()).isEqualTo(PLANNER);
                assertThat(record.action()).isEqualTo(StaffAction.EDIT_EVENT);
                assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.EVENT);
                assertThat(record.targetId()).isEqualTo(springPlay);
                assertThat(record.summary()).isEqualTo("행사 포스터 올리기 (1단계)");
                assertThat(record.operatedAt()).isEqualTo(LATER);
            });
        }

        @Test
        @DisplayName("잠금 순서 — 계정 줄 → 주최자 줄(외래 키 확인 잠금) → 행사 줄(FOR UPDATE), 위저드 단계 저장과 같다 (#195)")
        void locksAccountOrganizerThenEvent() {
            int locksBefore = scene.rowLockLog.locks().size();

            upload(PLANNER, threeByFourJpeg());

            assertThat(scene.rowLockLog.locks().subList(locksBefore, scene.rowLockLog.locks().size())).containsExactly(
                    "계정 " + PLANNER + " FOR KEY SHARE", "주최자 " + scene.twelveMonths + " FOR KEY SHARE",
                    "행사 " + springPlay + " FOR UPDATE");
        }

        @Test
        @DisplayName("다시 올리면 새 열쇠로 저장하고, 커밋된 뒤 바꾸기 전 포스터의 파일을 지운다 — 기본 정보는 그대로다")
        void replacingDeletesPreviousFileAfterCommit() {
            upload(JUNGHOON, threeByFourJpeg());
            scene.clock.moveTo(LATER);

            EventWizardResult result = upload(PLANNER, EventPosterImage.of("image/png", TestImages.png(900, 1200)));

            assertThat(scene.posterStorage.storedKeys()).containsExactly(SECOND_TOKEN + ".png");
            assertThat(result.posterUrl()).endsWith(SECOND_TOKEN + ".png");
            assertThat(result.lastModifiedAt()).isEqualTo(LATER);
            assertThat(result.basicInformation().title()).isEqualTo("2026 봄 정기공연 〈열두 번째 달〉");
            assertThat(scene.recordRepository.records()).hasSize(3);
        }

        @Test
        @DisplayName("행사 저장에서 되돌려지면 새로 올린 파일을 지우고, 바꾸기 전 포스터와 그 파일은 그대로다")
        void rollbackDeletesNewFile() {
            upload(JUNGHOON, threeByFourJpeg());
            scene.eventRepository.failSaves();

            assertThatIllegalStateException().isThrownBy(() -> upload(PLANNER, threeByFourJpeg()));

            assertThat(scene.posterStorage.storedKeys()).containsExactly(FIRST_TOKEN + ".jpg");
            assertThat(scene.eventRepository.findById(springPlay).orElseThrow().poster())
                    .contains(new EventPoster(FIRST_TOKEN + ".jpg"));
            assertThat(scene.recordRepository.records()).hasSize(2);
        }

        @Test
        @DisplayName("계정 줄을 기다리는 사이 내보내졌으면 잠근 뒤 다시 확인해 막는다 (NOT_ORGANIZER_STAFF) — 행사 줄을 잠그지 않고 파일 · 포스터 · 기록이 남지 않는다")
        void removedWhileWaitingForLock() {
            scene.staffAccountRepository.runOnNextLock(() -> scene.staffRepository.saveEnded(
                    scene.staffRepository.findByOrganizerId(scene.twelveMonths).removeOperator(JUNGHOON, PLANNER, NOW)));

            assertThatExceptionOfType(PermissionDeniedException.class)
                    .isThrownBy(() -> upload(PLANNER, threeByFourJpeg()))
                    .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
            assertThat(scene.rowLockLog.locks()).doesNotContain("행사 " + springPlay + " FOR UPDATE");
            assertThat(scene.posterStorage.storedKeys()).isEmpty();
            assertThat(scene.eventRepository.findById(springPlay).orElseThrow().poster()).isEmpty();
            assertThat(scene.recordRepository.records()).hasSize(1);
        }

        @Test
        @DisplayName("다른 주최자의 운영진은 열두달 경로로 403, 자기 주최자 경로로 열두달 행사에 닿거나 없는 행사면 404다 — 파일이 남지 않는다")
        void outsidersCannotReachEvent() {
            assertThatExceptionOfType(PermissionDeniedException.class)
                    .isThrownBy(() -> upload(DOHYUN, scene.twelveMonths, springPlay, threeByFourJpeg()))
                    .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> upload(YEOREUM, scene.lateSummer, springPlay, threeByFourJpeg()))
                    .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> upload(JUNGHOON, scene.twelveMonths, springPlay + 100, threeByFourJpeg()))
                    .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
            assertThat(scene.posterStorage.storedKeys()).isEmpty();
            assertThat(scene.recordRepository.records()).hasSize(1);
        }

        @Test
        @DisplayName("예매가 열린 뒤(판매 중)에도 포스터를 바꾼다 — 제목 · 포스터 · 소개는 자유 변경")
        void replaceableAfterBookingOpened() {
            upload(JUNGHOON, threeByFourJpeg());
            storeSpringPlayIn(EventStatus.ON_SALE);

            EventWizardResult result = upload(PLANNER, threeByFourJpeg());

            assertThat(result.posterUrl()).endsWith(SECOND_TOKEN + ".jpg");
            assertThat(scene.posterStorage.storedKeys()).containsExactly(SECOND_TOKEN + ".jpg");
        }

        @Test
        @DisplayName("판매가 끝난 행사는 포스터가 고정이다 (EDIT_LOCKED_AFTER_SALES_CLOSED) — 파일도 저장도 기록도 남지 않고 지금 포스터는 그대로다")
        void lockedAfterSalesClosed() {
            upload(JUNGHOON, threeByFourJpeg());
            storeSpringPlayIn(EventStatus.SALES_CLOSED);

            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(() -> upload(PLANNER, threeByFourJpeg()))
                    .satisfies(rejected -> assertThat(rejected.errorCode())
                            .isEqualTo(EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED));
            assertThat(scene.posterStorage.storedKeys()).containsExactly(FIRST_TOKEN + ".jpg");
            assertThat(scene.eventRepository.findById(springPlay).orElseThrow().poster())
                    .contains(new EventPoster(FIRST_TOKEN + ".jpg"));
            assertThat(scene.recordRepository.records()).hasSize(2);
        }

        @Test
        @DisplayName("위저드 값을 다시 보면 올린 포스터의 공개 주소가 담긴다 — 올리기 전에는 비어 있다")
        void wizardShowsPosterUrl() {
            GetEventWizardQuery query = new GetEventWizardQuery(acting(PLANNER, scene.twelveMonths), springPlay);
            assertThat(scene.getEventWizardService().get(query).posterUrl()).isNull();

            upload(JUNGHOON, threeByFourJpeg());

            assertThat(scene.getEventWizardService().get(query).posterUrl())
                    .isEqualTo(FakeEventPosterStorage.PUBLIC_BASE_URL + "/" + FIRST_TOKEN + ".jpg");
        }
    }

    @Nested
    @DisplayName("파일 내주기")
    class ServeFile {

        private final GetEventPosterFileService fileService = scene.getEventPosterFileService();

        @Test
        @DisplayName("저장된 포스터 파일을 열쇠의 확장자로 정한 형식과 함께 내준다")
        void servesStoredFile() {
            upload(JUNGHOON, threeByFourJpeg());

            EventPosterFileResult file = fileService.get(new GetEventPosterFileQuery(FIRST_TOKEN + ".jpg"));

            assertThat(file.content()).isEqualTo(threeByFourJpeg().content());
            assertThat(file.mediaType()).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("바꾸기 전 포스터의 파일은 없다")
        void replacedPosterIsNotFound() {
            upload(JUNGHOON, threeByFourJpeg());
            upload(JUNGHOON, threeByFourJpeg());

            assertNotFound(FIRST_TOKEN + ".jpg");
            assertThat(fileService.get(new GetEventPosterFileQuery(SECOND_TOKEN + ".jpg")).mediaType()).isEqualTo("image/jpeg");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @DisplayName("포스터 열쇠의 꼴이 아니면 저장소에 묻지 않고 없다고 답한다")
        @ValueSource(strings = {"../../etc/passwd", "firstPosterTokenAAAAAAAAAAAAAAAA", "poster.jpg", "",
                "firstPosterTokenAAAAAAAAAAAAAAAA.svg"})
        void malformedKeyIsNotFound(String fileKey) {
            assertNotFound(fileKey);
        }

        private void assertNotFound(String fileKey) {
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> fileService.get(new GetEventPosterFileQuery(fileKey)))
                    .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.EVENT_POSTER_NOT_FOUND));
        }
    }
}
