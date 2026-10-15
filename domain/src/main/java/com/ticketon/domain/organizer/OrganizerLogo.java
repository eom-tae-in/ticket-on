package com.ticketon.domain.organizer;

import java.util.Objects;

/**
 * 주최자 로고 — 정사각형으로 잘라 올린 그림 파일(docs/01 "공개 페이지 — 로고").
 * <p>
 * <b>선택이다.</b> 없으면 이름 첫 글자 + 주최자마다 정해진 색으로 보인다({@link Organizer#initialAvatar}). 파일 자체는 파일 저장소에 있고
 * 주최자는 그 파일을 가리키는 열쇠만 갖는다. 부적절하거나 다른 단체를 흉내 내면 관리자가 초기화한다.
 *
 * @param fileKey 파일 저장소에서 로고 파일을 가리키는 열쇠. 앞뒤 공백을 걷는다.
 */
public record OrganizerLogo(String fileKey) {

    public OrganizerLogo {
        Objects.requireNonNull(fileKey, "fileKey");
        fileKey = fileKey.strip();
        if (fileKey.isEmpty()) {
            throw new IllegalArgumentException("로고 파일 열쇠가 비어 있습니다");
        }
    }
}
