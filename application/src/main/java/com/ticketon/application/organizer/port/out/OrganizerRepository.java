package com.ticketon.application.organizer.port.out;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerBusinessDetails;
import java.util.Optional;

/**
 * 주최자 저장소 — 나가는 포트.
 * <p>
 * 새 주최자 신청을 받으면 그 주최자를 심사 중으로 만들어 저장한다(docs/14 "전체 관계도" — 주최자 상태: 심사 중 / 활동 / 닫힘 / 영구 정지).
 * 승인되면 활동으로 바꾼다. 주최자는 지우지 않는다 — 닫혀도 '닫힘'으로 남고(docs/11 "데이터 보관"), 신청이 반려돼도 심사 중으로 남는다.
 * 주최자명은 소유자가 바꾸면 그 자리에서 고친다 — 바꾸기 전 이름은 주최자명 변경 이력({@link OrganizerNameChangeRepository})에 남는다.
 * 공개 정보(소개 · SNS 링크 · 로고 열쇠)도 주최자와 함께 저장한다. 로고 파일 자체는 파일 저장소({@link OrganizerLogoStoragePort})에 있고
 * 여기에는 그 열쇠만 둔다.
 * <p>
 * 사업자 여부 · 사업자 정보 · 정산 계좌({@link OrganizerBusinessDetails})도 주최자와 함께 저장하지만 {@link Organizer}에는 싣지 않는다 — 공개
 * 페이지처럼 주최자를 읽는 곳마다 민감한 값이 따라다니지 않게 따로 읽고 쓴다. 승인할 때 신청서의 값을 옮겨 적고({@link #saveApproval}), 그
 * 뒤로는 관리자가 승인한 변경만 저장한다({@link #saveBusinessDetails}).
 */
public interface OrganizerRepository {

    /**
     * 새 주최자를 저장하고, id가 붙은 주최자를 돌려준다.
     *
     * @param newOrganizer {@link Organizer#underReview}로 만든, id가 없는 주최자 — 공개 정보는 비어 있다
     */
    Organizer add(Organizer newOrganizer);

    /** 공개 정보(소개 · SNS 링크 · 로고)까지 함께 읽는다. */
    Optional<Organizer> findById(long id);

    /**
     * 주최자를 읽고, 이 트랜잭션이 끝날 때까지 그 줄을 다른 변경에서 잠근다 — 공개 정보처럼 읽고 고쳐 저장하는 일에 쓴다.
     * <p>
     * 두 운영진이 거의 동시에 로고를 바꾸면 뒤의 요청은 앞의 요청이 끝날 때까지 기다렸다가 바뀐 값을 읽는다. 둘이 같은 "바꾸기 전 로고"를 읽어
     * 먼저 올린 로고 파일이 아무도 가리키지 않은 채 남는 일이 없게 한다.
     * <p>
     * <b>잠금 순서 — 운영진 계정 줄 다음이다.</b> 부르기 전에, 이 트랜잭션이 넣을 줄(이력 · 조작 기록)이 가리킬 운영진 계정 줄을 먼저 잡는다
     * (organizer 서비스의 {@code StaffAccountLocks} — 주최 관리 · 관리자 흐름 전체의 잠금 순서 표). 이 줄을 쥔 채 그 계정 줄의 외래 키 확인
     * 잠금을 기다리면, 계정 줄을 먼저 잠그고 이 주최자를 가리키는 줄을 넣는 흐름(소유자 넘기기 등)과 맞물려 교착한다.
     */
    Optional<Organizer> findByIdForUpdate(long id);

    /**
     * 승인한 주최자의 상태(활동)와, 승인한 신청서의 사업자 여부 · 사업자 정보 · 정산 계좌를 함께 저장한다. 표에서 아직 심사 중일 때만 바꾼다 —
     * 읽은 뒤 다른 처리가 먼저 상태를 바꿨으면 덮어쓰지 않는다.
     *
     * @param approvedOrganizer {@link Organizer#approve}로 활동이 된 저장된 주최자
     * @param businessDetails   승인한 신청서의 사업자 여부 · 사업자 정보 · 정산 계좌
     *                          ({@link com.ticketon.domain.organizer.OrganizerApplicationForm#businessDetails})
     * @throws ConflictException        표의 주최자가 이미 심사 중이 아니면({@code ORGANIZER_NOT_UNDER_REVIEW})
     * @throws IllegalArgumentException 저장 전 주최자이거나 활동 상태가 아니면
     */
    void saveApproval(Organizer approvedOrganizer, OrganizerBusinessDetails businessDetails);

    /**
     * 그 주최자의 지금 사업자 여부 · 사업자 정보 · 정산 계좌. 없는 주최자이거나 아직 승인되지 않아(심사 중 · 반려) 값이 없으면 비어 있다.
     */
    Optional<OrganizerBusinessDetails> findBusinessDetails(long organizerId);

    /**
     * 관리자가 승인한 변경을 적용한 사업자 여부 · 사업자 정보 · 정산 계좌를 저장한다. 이름 · 상태 · 공개 정보는 건드리지 않는다.
     * {@link #findByIdForUpdate}로 주최자 줄을 잠근 뒤 읽은 값에 적용해 부른다 — 정산 계좌 승인과 사업자 정보 승인이 거의 동시여도 뒤의 승인은
     * 앞의 승인이 저장한 값을 읽고 적용하므로 서로의 값을 덮지 않는다.
     *
     * @param businessDetails {@link com.ticketon.domain.organizer.OrganizerChangeRequest#approve}가 돌려준 값
     * @throws IllegalArgumentException 표에 없는 주최자이면
     */
    void saveBusinessDetails(long organizerId, OrganizerBusinessDetails businessDetails);

    /**
     * 공개 정보(소개 · SNS 링크 · 로고)를 저장한다. 이름 · 상태는 건드리지 않는다. SNS 링크는 적은 순서대로 통째로 바꾼다.
     *
     * @param organizer 공개 정보를 바꾼 저장된 주최자({@link Organizer#changeIntroductionAndSnsLinks}, {@link Organizer#changeLogo},
     *                  {@link Organizer#removeLogo})
     * @throws IllegalArgumentException 저장 전 주최자이거나 표에 없는 주최자이면
     */
    void savePublicProfile(Organizer organizer);

    /**
     * 바꾼 주최자명을 저장한다. 상태 · 공개 정보는 건드리지 않는다. {@link #findByIdForUpdate}로 줄을 잠그고 읽은 뒤 부른다 — 두 요청이 거의
     * 동시에 이름을 바꿔도 뒤의 요청은 앞의 요청이 바꾼 이름을 읽고 바꾸므로, 이력의 "바꾸기 전 이름"이 실제로 바꾸기 전 이름과 어긋나지 않는다.
     *
     * @param renamedOrganizer {@link Organizer#rename}으로 이름을 바꾼 저장된 주최자
     * @throws IllegalArgumentException 저장 전 주최자이거나 표에 없는 주최자이면
     */
    void saveName(Organizer renamedOrganizer);
}
