package com.ticketon.domain.event;

import com.ticketon.domain.policy.QuantityPolicy;
import java.util.Objects;

/**
 * 행사 포스터 — 파일 저장소에 올린 그림 파일을 가리키는 열쇠(docs/03 "기본 정보").
 * <p>
 * <b>{@link QuantityPolicy#POSTER_ASPECT_RATIO 3:4 세로} 고정이다</b> — 비율이 제각각이면 목록 카드가 지저분해진다. 화면이 업로드 후 크롭
 * 도구를 주고, 서버는 올릴 때 그림 크기로 비율과 용량 상한을 확인한다. 파일 자체는 파일 저장소에 있고 행사는 그 열쇠만 갖는다.
 * <p>
 * 포스터는 1단계의 필수 항목이지만 행사를 만든 뒤 따로 올린다 — 올릴 행사가 먼저 있어야 하기 때문이다. 그래서 행사는 포스터 없이 작성 중일 수
 * 있고, 판매를 시작하기 전에 갖춰야 한다.
 *
 * @param fileKey 파일 저장소에서 포스터 파일을 가리키는 열쇠. 앞뒤 공백을 걷는다.
 */
public record EventPoster(String fileKey) {

    public EventPoster {
        Objects.requireNonNull(fileKey, "fileKey");
        fileKey = fileKey.strip();
        if (fileKey.isEmpty()) {
            throw new IllegalArgumentException("포스터 파일 열쇠가 비어 있습니다");
        }
    }
}
