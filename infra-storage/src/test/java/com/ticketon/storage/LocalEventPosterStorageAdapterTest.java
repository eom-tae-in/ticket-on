package com.ticketon.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.ImageFormat;
import com.ticketon.common.TestImages;
import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.event.EventPosterImage;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("행사 포스터 파일 — 로컬 디스크 어댑터")
class LocalEventPosterStorageAdapterTest {

    private static final EventPoster POSTER = EventPoster.issue("Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL", ImageFormat.JPEG);

    @TempDir
    private Path rootDirectory;

    private LocalEventPosterStorageAdapter adapter;

    @BeforeEach
    void createAdapter() {
        adapter = new LocalEventPosterStorageAdapter(
                new LocalFileStore(rootDirectory, "http://localhost:8080/api/public/files"));
    }

    @Test
    @DisplayName("포스터 파일을 event-posters 묶음에 열쇠를 이름으로 저장하고, 그대로 읽는다 — 로고 묶음과 섞이지 않는다")
    void storesUnderPosterBundle() {
        EventPosterImage image = EventPosterImage.of("image/jpeg", TestImages.jpeg(300, 400));

        adapter.store(POSTER, image);

        assertThat(rootDirectory.resolve("event-posters").resolve(POSTER.fileKey())).hasBinaryContent(image.content());
        assertThat(rootDirectory.resolve("organizer-logos")).doesNotExist();
        assertThat(adapter.read(POSTER)).hasValueSatisfying(content -> assertThat(content).isEqualTo(image.content()));
    }

    @Test
    @DisplayName("지운 포스터 파일은 더는 읽히지 않는다 — 이미 없어도 지우기는 아무것도 하지 않는다")
    void deletes() {
        adapter.store(POSTER, EventPosterImage.of("image/png", TestImages.png(30, 40)));

        adapter.delete(POSTER);
        adapter.delete(POSTER);

        assertThat(adapter.read(POSTER)).isEmpty();
    }

    @Test
    @DisplayName("공개 주소는 API 서버의 파일 공개 경로 아래 event-posters/열쇠다")
    void publicUrl() {
        assertThat(adapter.publicUrlOf(POSTER))
                .isEqualTo("http://localhost:8080/api/public/files/event-posters/Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.jpg");
    }
}
