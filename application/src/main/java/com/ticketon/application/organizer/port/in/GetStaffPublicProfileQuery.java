package com.ticketon.application.organizer.port.in;

/**
 * 운영진 프로필 조회 입력. 로그인 없이 누구나 보므로 보는 사람을 담지 않는다.
 *
 * @param staffAccountId 프로필을 볼 운영진 계정 id (1 이상)
 */
public record GetStaffPublicProfileQuery(long staffAccountId) {

    public GetStaffPublicProfileQuery {
        if (staffAccountId < 1) {
            throw new IllegalArgumentException("운영진 계정 id는 1 이상이어야 합니다: " + staffAccountId);
        }
    }
}
