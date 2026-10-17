package com.ticketon.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.event.port.out.EventPosterStoragePort;
import com.ticketon.application.organizer.port.out.OrganizerLogoStoragePort;
import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.organizer.OrganizerLogo;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * 실행 모듈은 com.ticketon 전체를 스캔해 runtimeOnly로 묶인 어댑터를 포트에 연결한다. 이 모듈만 스캔해도 로고 · 포스터 파일 저장 포트가 로컬
 * 디스크 어댑터로 채워지고, 저장 위치 · 공개 기준 주소를 설정에서 읽는지 본다.
 */
@DisplayName("로컬 파일 저장 어댑터의 빈 등록")
class LocalStorageRegistrationTest {

    @TempDir
    private Path rootDirectory;

    @Test
    @DisplayName("로고 파일 저장 포트를 로컬 디스크 어댑터가 구현하고, 공개 주소는 설정한 기준 주소로 시작한다")
    void logoStoragePortIsLocalAdapter() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "ticketon.storage.local.directory", rootDirectory.toString(),
                    "ticketon.storage.local.public-base-url", "https://files.ticket-on.example/api/public/files")));
            context.scan("com.ticketon.storage");
            context.refresh();

            OrganizerLogoStoragePort port = context.getBean(OrganizerLogoStoragePort.class);

            assertThat(port).isInstanceOf(LocalOrganizerLogoStorageAdapter.class);
            assertThat(port.publicUrlOf(new OrganizerLogo("Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK.jpg"))).isEqualTo(
                    "https://files.ticket-on.example/api/public/files/organizer-logos/Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK.jpg");
        }
    }

    @Test
    @DisplayName("포스터 파일 저장 포트도 같은 저장소를 쓰는 로컬 디스크 어댑터가 구현하고, 공개 주소는 event-posters 아래다")
    void posterStoragePortIsLocalAdapter() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "ticketon.storage.local.directory", rootDirectory.toString(),
                    "ticketon.storage.local.public-base-url", "https://files.ticket-on.example/api/public/files")));
            context.scan("com.ticketon.storage");
            context.refresh();

            EventPosterStoragePort port = context.getBean(EventPosterStoragePort.class);

            assertThat(port).isInstanceOf(LocalEventPosterStorageAdapter.class);
            assertThat(port.publicUrlOf(new EventPoster("Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.png"))).isEqualTo(
                    "https://files.ticket-on.example/api/public/files/event-posters/Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.png");
        }
    }

    @Test
    @DisplayName("저장 위치가 비어 있으면 뜨지 않는다 — 빈 경로는 서버를 띄운 폴더(레포 등)가 된다")
    void blankDirectoryIsRejected() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "ticketon.storage.local.directory", " ",
                    "ticketon.storage.local.public-base-url", "http://localhost:8080/api/public/files")));
            context.scan("com.ticketon.storage");

            assertThatExceptionOfType(BeanCreationException.class).isThrownBy(context::refresh)
                    .havingRootCause().isInstanceOf(IllegalArgumentException.class);
        }
    }
}
