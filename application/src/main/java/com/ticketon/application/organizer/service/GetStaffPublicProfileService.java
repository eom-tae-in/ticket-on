package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.organizer.port.in.GetStaffPublicProfileQuery;
import com.ticketon.application.organizer.port.in.GetStaffPublicProfileUseCase;
import com.ticketon.application.organizer.port.in.StaffPublicProfileResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffIntroduction;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAffiliations;
import com.ticketon.domain.organizer.StaffPublicProfile;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영진 프로필 조회 — 그 사람의 계정 · 모든 소속 · 소속된 적이 있는 주최자를 읽어, 누가 프로필을 갖고 무엇이 보이는지는
 * {@link StaffPublicProfile}에 맡긴다. 공개되는 칸만 결과로 옮긴다 — 계정의 이름 · 이메일은 옮기지 않는다.
 * <p>
 * 프로필이 없는 경우(없는 계정 · 소속된 적 없음 · 탈퇴)는 모두 같은 404다 — 공개 페이지에서 어느 경우인지 드러나지 않게.
 */
@Service
public class GetStaffPublicProfileService implements GetStaffPublicProfileUseCase {

    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerStaffRepository staffRepository;
    private final OrganizerRepository organizerRepository;

    public GetStaffPublicProfileService(StaffAccountRepository staffAccountRepository,
            OrganizerStaffRepository staffRepository, OrganizerRepository organizerRepository) {
        this.staffAccountRepository = staffAccountRepository;
        this.staffRepository = staffRepository;
        this.organizerRepository = organizerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public StaffPublicProfileResult getProfile(GetStaffPublicProfileQuery query) {
        long staffAccountId = query.staffAccountId();
        StaffAccount account = staffAccountRepository.findById(staffAccountId)
                .orElseThrow(() -> profileNotFound(staffAccountId));
        StaffAffiliations affiliations = staffRepository.findAffiliationsOf(staffAccountId);
        return StaffPublicProfile.of(account, affiliations, participatedOrganizersOf(affiliations))
                .map(GetStaffPublicProfileService::toResult)
                .orElseThrow(() -> profileNotFound(staffAccountId));
    }

    private List<Organizer> participatedOrganizersOf(StaffAffiliations affiliations) {
        return affiliations.participatedOrganizerIds().stream()
                .map(organizerId -> organizerRepository.findById(organizerId)
                        .orElseThrow(() -> new IllegalStateException("소속이 가리키는 주최자가 없습니다: 주최자 " + organizerId)))
                .toList();
    }

    private static StaffPublicProfileResult toResult(StaffPublicProfile profile) {
        return new StaffPublicProfileResult(profile.nickname().value(),
                profile.introduction().map(StaffIntroduction::value).orElse(null),
                profile.initialAvatar().initial(), profile.initialAvatar().paletteIndex(),
                profile.participatedOrganizers().stream()
                        .map(participation -> new StaffPublicProfileResult.ParticipatedOrganizer(
                                participation.organizerId(), participation.organizerName().value(), participation.role(),
                                participation.startedAt(), participation.endedAt(), participation.activityEnded()))
                        .toList());
    }

    private static NotFoundException profileNotFound(long staffAccountId) {
        return new NotFoundException(OrganizerErrorCode.STAFF_PROFILE_NOT_FOUND, "운영진 계정 " + staffAccountId);
    }
}
