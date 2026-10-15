package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffPublicProfile;

/**
 * 운영진 프로필 조회 — 들어오는 포트. 로그인 없이 누구나 본다(docs/01 "공개 페이지 — 운영진 프로필", docs/13 "1. 공개 영역 — 운영진 프로필").
 * <p>
 * 닉네임 · 한 줄 소개 · 첫 글자 표시 · 참여한 주최자(이름 · 역할 · 기간, 나왔거나 닫힌 주최자는 활동 종료)만 돌려준다. 실명 · 이메일 · 관객으로서
 * 예매한 내역은 돌려주지 않는다. 누가 프로필을 갖고 무엇이 보이는지는 {@link StaffPublicProfile}이 정한다. 보기만 하므로 기록을 남기지 않는다.
 */
public interface GetStaffPublicProfileUseCase {

    /**
     * @throws NotFoundException 프로필이 없으면 — 없는 운영진 로그인, 주최자에 소속된 적이 없는 로그인, 탈퇴한 로그인
     *                           ({@link OrganizerErrorCode#STAFF_PROFILE_NOT_FOUND})
     */
    StaffPublicProfileResult getProfile(GetStaffPublicProfileQuery query);
}
