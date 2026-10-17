package com.ticketon.application.event.port.out;

import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.event.EventPosterImage;
import java.util.Optional;

/**
 * 행사 포스터 파일 저장소 — 나가는 포트.
 * <p>
 * 파일 업체는 배포 때 정한다(docs/16 "문자 · 이메일 · 파일 — 배포 때 정함"). 개발 중엔 로컬 디스크에 저장하는 어댑터(infra-storage)가 구현하고,
 * 업체를 정하면 어댑터만 더한다. 파일은 행사가 가진 열쇠({@link EventPoster})로 가리킨다 — 열쇠는 추측할 수 없는 값이라 겹치지 않고, 누구나 여는
 * 공개 주소에 그대로 들어간다(관객 화면 · 카톡 링크 미리보기가 로그인 없이 포스터를 연다).
 * <p>
 * 저장소는 트랜잭션에 묶이지 않는다. 포스터를 바꾸는 유스케이스는 새 파일을 먼저 저장하고, 행사 저장이 커밋된 뒤에 바꾸기 전 파일을 지우며,
 * 되돌려지면 새 파일을 지운다.
 */
public interface EventPosterStoragePort {

    /**
     * 포스터 파일을 그 열쇠로 저장한다. 저장이 끝나면 {@link #read}와 공개 주소로 열 수 있다.
     *
     * @param poster 새로 붙인 열쇠({@link EventPoster#issue})
     * @param image  포스터로 받을 수 있다고 확인한 그림
     */
    void store(EventPoster poster, EventPosterImage image);

    /** 포스터 파일을 지운다. 지운 뒤에는 공개 주소로 열리지 않는다. 이미 없으면 아무것도 하지 않는다. */
    void delete(EventPoster poster);

    /** 누구나 로그인 없이 그 포스터 파일을 여는 공개 주소(절대 주소). 화면은 이 주소를 그림 주소로 그대로 쓴다. */
    String publicUrlOf(EventPoster poster);

    /**
     * 저장된 포스터 파일의 내용. 공개 주소가 이 서버를 가리킬 때(로컬 저장) 서버가 파일을 내주는 데 쓴다.
     *
     * @return 없으면 빈 값
     */
    Optional<byte[]> read(EventPoster poster);
}
