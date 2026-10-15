package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.out.OrganizerNameChangeRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerNameChange;
import com.ticketon.domain.organizer.OrganizerNameChangeEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 메모리 주최자명 변경 이력 저장소 — 저장한 순서대로 id를 매기고, 확인 저장은 아직 확인 전일 때만 한다. 읽을 때마다 새 객체다. */
final class FakeOrganizerNameChangeRepository implements OrganizerNameChangeRepository {

    private final List<OrganizerNameChangeEntry> entries = new ArrayList<>();

    /** 저장된 이력 전부 — 저장한 순서대로. */
    List<OrganizerNameChangeEntry> savedEntries() {
        return entries.stream().map(FakeOrganizerNameChangeRepository::copyOf).toList();
    }

    @Override
    public OrganizerNameChangeEntry add(long organizerId, OrganizerNameChange change) {
        OrganizerNameChangeEntry added = OrganizerNameChangeEntry.restore(entries.size() + 1L, organizerId, change, null,
                null);
        entries.add(added);
        return copyOf(added);
    }

    @Override
    public Optional<OrganizerNameChangeEntry> findById(long id) {
        return entries.stream().filter(entry -> entry.id() == id).findFirst()
                .map(FakeOrganizerNameChangeRepository::copyOf);
    }

    @Override
    public void saveConfirmation(OrganizerNameChangeEntry confirmedEntry) {
        int index = (int) confirmedEntry.id() - 1;
        if (entries.get(index).isConfirmed()) {
            throw new ConflictException(OrganizerErrorCode.ORGANIZER_NAME_CHANGE_ALREADY_CONFIRMED, "확인 경합(흉내)");
        }
        entries.set(index, copyOf(confirmedEntry));
    }

    private static OrganizerNameChangeEntry copyOf(OrganizerNameChangeEntry entry) {
        return OrganizerNameChangeEntry.restore(entry.id(), entry.organizerId(), entry.change(),
                entry.confirmedAt().orElse(null), entry.confirmedBy().orElse(null));
    }
}
