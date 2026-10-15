package com.ticketon.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("로컬 디스크 파일 저장소")
class LocalFileStoreTest {

    private static final String FILE_NAME = "Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK.png";
    private static final byte[] CONTENT = "로고".getBytes(StandardCharsets.UTF_8);

    @TempDir
    private Path workDirectory;

    private Path rootDirectory;
    private LocalFileStore store;

    @BeforeEach
    void createStore() {
        rootDirectory = workDirectory.resolve("storage");
        store = new LocalFileStore(rootDirectory, "http://localhost:8080/api/public/files/");
    }

    @Test
    @DisplayName("쓴 파일은 저장 위치/묶음/파일 이름에 있고 그대로 읽힌다 — 저장 위치가 없으면 처음 쓸 때 만든다")
    void writesAndReads() {
        store.write("organizer-logos", FILE_NAME, CONTENT);

        assertThat(rootDirectory.resolve("organizer-logos").resolve(FILE_NAME)).hasBinaryContent(CONTENT);
        assertThat(store.read("organizer-logos", FILE_NAME)).hasValueSatisfying(read -> assertThat(read).isEqualTo(CONTENT));
    }

    @Test
    @DisplayName("다 쓴 뒤에는 임시 파일이 남지 않는다")
    void leavesNoTemporaryFile() throws IOException {
        store.write("organizer-logos", FILE_NAME, CONTENT);

        try (Stream<Path> files = Files.list(rootDirectory.resolve("organizer-logos"))) {
            assertThat(files).extracting(path -> path.getFileName().toString()).containsExactly(FILE_NAME);
        }
    }

    @Test
    @DisplayName("없는 파일은 빈 값으로 읽히고, 지우면 다시 읽히지 않는다 — 없는 파일을 지워도 된다")
    void deletes() {
        assertThat(store.read("organizer-logos", FILE_NAME)).isEmpty();
        store.write("organizer-logos", FILE_NAME, CONTENT);

        store.delete("organizer-logos", FILE_NAME);
        store.delete("organizer-logos", FILE_NAME);

        assertThat(store.read("organizer-logos", FILE_NAME)).isEmpty();
        assertThat(rootDirectory.resolve("organizer-logos").resolve(FILE_NAME)).doesNotExist();
    }

    @Test
    @DisplayName("묶음이 다르면 같은 이름이라도 다른 파일이다")
    void bundlesAreSeparate() {
        store.write("organizer-logos", FILE_NAME, CONTENT);

        assertThat(store.read("event-posters", FILE_NAME)).isEmpty();
    }

    @Test
    @DisplayName("공개 주소는 공개 기준 주소/묶음/파일 이름이다 — 기준 주소 끝의 '/'는 떼어 낸다")
    void publicUrl() {
        assertThat(store.publicUrlOf("organizer-logos", FILE_NAME))
                .isEqualTo("http://localhost:8080/api/public/files/organizer-logos/" + FILE_NAME);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @DisplayName("경로를 벗어나거나 여러 덩어리인 이름은 쓰지도 읽지도 지우지도 않는다")
    @ValueSource(strings = {"", ".", "..", "../outside.png", "a/b.png", "/etc/passwd", "a\\b.png", "logo..png", ".png",
            "logo.PNG", "로고.png", "logo.png "})
    void rejectsUnsafeNames(String fileName) {
        assertThatIllegalArgumentException().isThrownBy(() -> store.write("organizer-logos", fileName, CONTENT));
        assertThatIllegalArgumentException().isThrownBy(() -> store.read("organizer-logos", fileName));
        assertThatIllegalArgumentException().isThrownBy(() -> store.delete("organizer-logos", fileName));
        assertThatIllegalArgumentException().isThrownBy(() -> store.publicUrlOf("organizer-logos", fileName));
        assertThatIllegalArgumentException().isThrownBy(() -> store.write(fileName, FILE_NAME, CONTENT));
        assertThat(workDirectory.resolve("outside.png")).doesNotExist();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @DisplayName("공개 기준 주소는 쿼리 없는 http(s) 절대 주소여야 한다")
    @ValueSource(strings = {"/api/public/files", "ftp://localhost/files", "http://localhost:8080/files?x=1", "localhost:8080"})
    void requiresWebBaseUrl(String publicBaseUrl) {
        assertThatIllegalArgumentException().isThrownBy(() -> new LocalFileStore(rootDirectory, publicBaseUrl));
    }
}
