package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.EventPosterFileResult;
import com.ticketon.application.event.port.in.GetEventPosterFileQuery;
import com.ticketon.application.event.port.in.GetEventPosterFileUseCase;
import com.ticketon.application.event.port.out.EventPosterStoragePort;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventPoster;
import org.springframework.stereotype.Service;

/**
 * 행사 포스터 파일 내주기 — 들어온 열쇠가 포스터 열쇠의 꼴인지({@link EventPoster#isValidFileKey}) 먼저 보고, 맞을 때만 파일 저장소에서 읽는다.
 * 경로를 거스르는 이름이나 다른 꼴의 값은 저장소에 닿지 않는다. 형식(Content-Type)은 열쇠의 확장자로 정한다 — 저장할 때 실제 바이트의 형식으로
 * 붙인 확장자다. DB를 읽지 않아 트랜잭션을 열지 않는다.
 */
@Service
public class GetEventPosterFileService implements GetEventPosterFileUseCase {

    private final EventPosterStoragePort posterStorage;

    public GetEventPosterFileService(EventPosterStoragePort posterStorage) {
        this.posterStorage = posterStorage;
    }

    @Override
    public EventPosterFileResult get(GetEventPosterFileQuery query) {
        if (!EventPoster.isValidFileKey(query.fileKey())) {
            throw notFound(query.fileKey());
        }
        EventPoster poster = new EventPoster(query.fileKey());
        byte[] content = posterStorage.read(poster).orElseThrow(() -> notFound(query.fileKey()));
        return new EventPosterFileResult(content, poster.format().mediaType());
    }

    private static NotFoundException notFound(String fileKey) {
        return new NotFoundException(EventErrorCode.EVENT_POSTER_NOT_FOUND, "포스터 열쇠 " + fileKey);
    }
}
