package com.ticketon.application.admin.service;

import com.ticketon.application.organizer.port.out.OwnershipChangeRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OwnershipChange;
import com.ticketon.domain.organizer.OwnershipTransfer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 메모리 소유자 변경 이력 저장소 — 저장한 순서대로 id를 매기고, 확인 저장은 아직 확인 전일 때만 한다. 읽을 때마다 새 객체다. */
final class FakeOwnershipChangeRepository implements OwnershipChangeRepository {

    private final List<OwnershipChange> changes = new ArrayList<>();

    /** 저장된 이력 전부 — 저장한 순서대로. */
    List<OwnershipChange> savedChanges() {
        return changes.stream().map(FakeOwnershipChangeRepository::copyOf).toList();
    }

    @Override
    public OwnershipChange add(OwnershipTransfer transfer) {
        OwnershipChange added = OwnershipChange.restore(changes.size() + 1L, transfer, null, null);
        changes.add(added);
        return copyOf(added);
    }

    @Override
    public Optional<OwnershipChange> findById(long id) {
        return changes.stream().filter(change -> change.id() == id).findFirst().map(FakeOwnershipChangeRepository::copyOf);
    }

    @Override
    public void saveConfirmation(OwnershipChange confirmedChange) {
        int index = (int) confirmedChange.id() - 1;
        if (changes.get(index).isConfirmed()) {
            throw new ConflictException(OrganizerErrorCode.OWNERSHIP_CHANGE_ALREADY_CONFIRMED, "확인 경합(흉내)");
        }
        changes.set(index, copyOf(confirmedChange));
    }

    private static OwnershipChange copyOf(OwnershipChange change) {
        return OwnershipChange.restore(change.id(), change.transfer(), change.confirmedAt().orElse(null),
                change.confirmedBy().orElse(null));
    }
}
