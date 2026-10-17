package com.ticketon.storage;

import com.ticketon.application.event.port.out.EventPosterStoragePort;
import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.event.EventPosterImage;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 행사 포스터 파일 — 로컬 디스크 어댑터. 파일은 {@link LocalFileStore}의 {@value #BUNDLE} 묶음에 포스터 열쇠를 파일 이름으로 둔다 — 주최자 로고
 * 어댑터({@link LocalOrganizerLogoStorageAdapter})와 묶음 이름만 다르다.
 * <p>
 * 공개 주소는 {@code 공개 기준 주소/event-posters/열쇠}이고, API 서버가 그 경로({@code /api/public/files/event-posters/{열쇠}})로 들어온 요청에
 * 이 어댑터가 읽은 파일을 내준다. 업체 어댑터를 더하면 공개 주소는 업체의 주소가 된다.
 */
@Component
public class LocalEventPosterStorageAdapter implements EventPosterStoragePort {

    /** 포스터 파일을 두는 묶음 — 공개 주소에도 그대로 들어간다. */
    static final String BUNDLE = "event-posters";

    private final LocalFileStore fileStore;

    public LocalEventPosterStorageAdapter(LocalFileStore fileStore) {
        this.fileStore = fileStore;
    }

    @Override
    public void store(EventPoster poster, EventPosterImage image) {
        fileStore.write(BUNDLE, poster.fileKey(), image.content());
    }

    @Override
    public void delete(EventPoster poster) {
        fileStore.delete(BUNDLE, poster.fileKey());
    }

    @Override
    public String publicUrlOf(EventPoster poster) {
        return fileStore.publicUrlOf(BUNDLE, poster.fileKey());
    }

    @Override
    public Optional<byte[]> read(EventPoster poster) {
        return fileStore.read(BUNDLE, poster.fileKey());
    }
}
