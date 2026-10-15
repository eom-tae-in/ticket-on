package com.ticketon.application.organizer.port.out;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerNameChange;
import com.ticketon.domain.organizer.OrganizerNameChangeEntry;
import java.util.Optional;

/**
 * 주최자명 변경 이력 저장소 — 나가는 포트. 이력은 지우지 않는다({@link OrganizerNameChangeEntry}).
 * <p>
 * 소유자가 주최자명을 바꾸면 한 줄을 더하고({@link #add}), 관리자가 사후 확인하면 확인을 남긴다({@link #saveConfirmation}). 확인 전인 이력은
 * 관리자 처리 목록에 '주최자명 변경'으로 쌓인다(처리 목록 조회가 이 이력을 읽는다).
 */
public interface OrganizerNameChangeRepository {

    /**
     * 막 바꾼 한 건을 확인 전인 이력으로 저장하고, id가 붙은 이력을 돌려준다.
     *
     * @param organizerId 이름을 바꾼 주최자 id
     * @param change      {@link com.ticketon.domain.organizer.Organizer#rename}이 돌려준 바꾼 한 건
     */
    OrganizerNameChangeEntry add(long organizerId, OrganizerNameChange change);

    Optional<OrganizerNameChangeEntry> findById(long id);

    /**
     * 관리자의 사후 확인을 저장한다. 표에서 그 이력이 아직 확인 전일 때만 바꾼다.
     *
     * @param confirmedEntry {@link OrganizerNameChangeEntry#confirm}으로 확인한 이력
     * @throws ConflictException 그 사이 다른 관리자가 먼저 확인했으면({@code ORGANIZER_NAME_CHANGE_ALREADY_CONFIRMED})
     */
    void saveConfirmation(OrganizerNameChangeEntry confirmedEntry);
}
