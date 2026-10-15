package com.ticketon.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.ImageFormat;
import com.ticketon.common.TestImages;
import com.ticketon.domain.organizer.OrganizerLogo;
import com.ticketon.domain.organizer.OrganizerLogoImage;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("주최자 로고 파일 — 로컬 디스크 어댑터")
class LocalOrganizerLogoStorageAdapterTest {

    private static final OrganizerLogo LOGO = OrganizerLogo.issue("Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK", ImageFormat.PNG);

    @TempDir
    private Path rootDirectory;

    private LocalOrganizerLogoStorageAdapter adapter;

    @BeforeEach
    void createAdapter() {
        adapter = new LocalOrganizerLogoStorageAdapter(
                new LocalFileStore(rootDirectory, "http://localhost:8080/api/public/files"));
    }

    @Test
    @DisplayName("로고 파일을 organizer-logos 묶음에 열쇠를 이름으로 저장하고, 그대로 읽는다")
    void storesUnderLogoBundle() {
        OrganizerLogoImage image = OrganizerLogoImage.of("image/png", TestImages.png(256, 256));

        adapter.store(LOGO, image);

        assertThat(rootDirectory.resolve("organizer-logos").resolve(LOGO.fileKey())).hasBinaryContent(image.content());
        assertThat(adapter.read(LOGO)).hasValueSatisfying(content -> assertThat(content).isEqualTo(image.content()));
    }

    @Test
    @DisplayName("지운 로고 파일은 더는 읽히지 않는다")
    void deletes() {
        adapter.store(LOGO, OrganizerLogoImage.of("image/png", TestImages.png(64, 64)));

        adapter.delete(LOGO);

        assertThat(adapter.read(LOGO)).isEmpty();
    }

    @Test
    @DisplayName("공개 주소는 API 서버의 파일 공개 경로 아래 organizer-logos/열쇠다")
    void publicUrl() {
        assertThat(adapter.publicUrlOf(LOGO))
                .isEqualTo("http://localhost:8080/api/public/files/organizer-logos/Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK.png");
    }
}
