package com.ticketon.application.event.port.in;

import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.EventVisibility;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.event.SessionChoiceRestriction;
import com.ticketon.domain.event.TicketsPerPersonBasis;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 행사 위저드 값 — 행사 만들기 · 설정 편집 위저드가 저장된 단계의 칸을 채우고 이어서 할 단계를 아는 데 쓴다(docs/03 "행사 만들기 위저드", docs/04
 * "작성 중 행사 관리"). 지금은 1단계(기본 정보)와 5단계(공개 수준 · 예매 설정 · 취소 규정)를 담는다 — 2 · 3 · 4 · 6단계와 포스터는 그 기능이
 * 더한다.
 * <p>
 * 공개 주소는 링크 전용 행사를 여는 자격이라 문자열로 바꾸면 가린다.
 *
 * @param eventId          행사 id
 * @param status           저장된 행사 상태
 * @param wizardProgress   작성 진행 단계 — 작성 중에 저장한 가장 뒤의 단계
 * @param lastModifiedAt   마지막 수정 시각
 * @param publicAddress    공개 주소 — 관객 링크에 들어가는 추측할 수 없는 값
 * @param basicInformation 1단계 기본 정보
 * @param bookingStep      5단계 공개 수준 · 예매 설정 · 취소 규정
 */
public record EventWizardResult(long eventId, EventStatus status, EventWizardStep wizardProgress, Instant lastModifiedAt,
        String publicAddress, BasicInformation basicInformation, BookingStep bookingStep) {

    public EventWizardResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(wizardProgress, "wizardProgress");
        Objects.requireNonNull(lastModifiedAt, "lastModifiedAt");
        Objects.requireNonNull(publicAddress, "publicAddress");
        Objects.requireNonNull(basicInformation, "basicInformation");
        Objects.requireNonNull(bookingStep, "bookingStep");
    }

    @Override
    public String toString() {
        return "EventWizardResult[eventId=" + eventId + ", status=" + status + ", wizardProgress=" + wizardProgress
                + ", lastModifiedAt=" + lastModifiedAt + ", publicAddress=****, basicInformation=" + basicInformation
                + ", bookingStep=" + bookingStep + "]";
    }

    /**
     * 1단계 기본 정보 — 저장된 그대로(앞뒤 공백을 걷고 NFC로 맞춘 값). 공개용 연락처는 문자열로 바꾸면 가린다.
     *
     * @param title         제목
     * @param type          유형
     * @param categoryName  고른 카테고리 이름 — 기타면 "기타"
     * @param genreName     기타를 고르고 적은 장르명. 정식 카테고리면 null
     * @param introduction  소개
     * @param venueName     공연장 이름
     * @param venueAddress  주소
     * @param venueDetails  상세 안내. 없으면 null
     * @param mapLink       지도 링크. 없으면 null
     * @param publicContact 공개용 연락처. 없으면 null
     */
    public record BasicInformation(String title, EventType type, String categoryName, String genreName, String introduction,
            String venueName, String venueAddress, String venueDetails, String mapLink, String publicContact) {

        @Override
        public String toString() {
            return "BasicInformation[title=" + title + ", type=" + type + ", categoryName=" + categoryName + ", genreName="
                    + genreName + ", venueName=" + venueName + ", publicContact=" + (publicContact == null ? null : "****")
                    + "]";
        }
    }

    /**
     * 5단계 — 공개 수준 · 예매 설정 · 취소 규정. 건너뛰었으면 처음 값이다.
     *
     * @param visibility                 공개 수준
     * @param passwordSet                암호가 걸려 있는지 — 암호 보호일 때만 true. 암호는 해시로만 있어 보여 줄 수 없다
     * @param bookingOpensAt             예매 오픈 시각. 비워 두었으면 null — 판매를 시작하는 순간 연다
     * @param bookingCloseLeadTime       예매 종료 — 회차 시작(종일형은 마지막 입장)보다 앞서는 시간
     * @param checkinOpenLeadTime        검표 시작 — 회차 시작(종일형은 운영 시작)보다 앞서는 시간
     * @param maxSeatsPerSelection       1회 선택 매수
     * @param maxTicketsPerPerson        1인 총 매수의 한도. 제한이 없으면 null
     * @param ticketsPerPersonBasis      1인 총 매수를 세는 범위. 제한이 없으면 null
     * @param sessionChoice              회차 선택 제약
     * @param reentryAllowed             재입장을 켰는지
     * @param minimumAge                 연령 제한의 기준 나이(만 이 나이 이상). 제한이 없으면 null
     * @param cancellationFeeTiers       취소 규정 구간 — 먼 날부터
     * @param cancellationPolicyStandard 취소 규정이 기준표 그대로인지
     */
    public record BookingStep(EventVisibility visibility, boolean passwordSet, Instant bookingOpensAt,
            Duration bookingCloseLeadTime, Duration checkinOpenLeadTime, int maxSeatsPerSelection, Integer maxTicketsPerPerson,
            TicketsPerPersonBasis ticketsPerPersonBasis, SessionChoiceRestriction sessionChoice, boolean reentryAllowed,
            Integer minimumAge, List<CancellationFeeTier> cancellationFeeTiers, boolean cancellationPolicyStandard) {

        public BookingStep {
            Objects.requireNonNull(visibility, "visibility");
            Objects.requireNonNull(bookingCloseLeadTime, "bookingCloseLeadTime");
            Objects.requireNonNull(checkinOpenLeadTime, "checkinOpenLeadTime");
            Objects.requireNonNull(sessionChoice, "sessionChoice");
            cancellationFeeTiers = List.copyOf(Objects.requireNonNull(cancellationFeeTiers, "cancellationFeeTiers"));
        }
    }

    /**
     * 취소 규정 구간 하나 — "관람일 N일 전부터 공제율 P%".
     *
     * @param fromDaysBefore       이 구간이 시작하는 날 — 관람일 이만큼 전부터(0은 당일)
     * @param deductionRatePercent 공제율(%)
     */
    public record CancellationFeeTier(int fromDaysBefore, int deductionRatePercent) {
    }
}
