package com.ticketon.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 로컬 디스크 파일 저장소 — 개발 중 쓰는 파일 저장(docs/16 "문자 · 이메일 · 파일 — 개발 중엔 로컬 저장"). 업체는 배포 때 정한다.
 * <p>
 * 파일은 {@code 저장 위치/묶음/파일 이름}에 둔다. 묶음은 파일 종류마다 하나다(주최자 로고는 {@code organizer-logos}, 포스터 등 다른 파일도
 * 묶음 이름만 달리해 이 저장소를 함께 쓴다). 공개 주소는 {@code 공개 기준 주소/묶음/파일 이름}이다 — 기준 주소는 이 서버의 파일 공개 경로를
 * 가리키고(app-api {@code /api/public/files}), 서버는 그 경로로 들어온 요청에 이 저장소의 파일을 내준다.
 * <p>
 * <b>경로를 벗어나지 않는다.</b> 묶음과 파일 이름은 URL 안전 문자와 확장자 하나로 된 한 덩어리만 받는다 — {@code /}, {@code ..}, 절대 경로가
 * 섞이면 거절한다. 그래도 만든 경로가 저장 위치 밖이면 거절한다. 파일 이름은 쓰는 쪽이 추측할 수 없는 값으로 붙인다.
 * <p>
 * 쓰기는 같은 폴더의 임시 파일에 다 쓴 뒤 이름을 바꿔 옮긴다 — 쓰는 도중의 반쪽 파일이 공개 주소로 나가지 않는다. 디스크 오류는
 * {@link UncheckedIOException}으로 알린다.
 */
public final class LocalFileStore {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]+(\\.[a-z0-9]+)?");

    private final Path rootDirectory;
    private final String publicBaseUrl;

    /**
     * @param rootDirectory 저장 위치 — 없으면 처음 쓸 때 만든다
     * @param publicBaseUrl 공개 기준 주소 — http(s) 절대 주소. 끝의 '/'는 떼어 낸다
     * @throws IllegalArgumentException 공개 기준 주소가 http(s) 절대 주소가 아니면
     */
    public LocalFileStore(Path rootDirectory, String publicBaseUrl) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory").toAbsolutePath().normalize();
        this.publicBaseUrl = webAddress(Objects.requireNonNull(publicBaseUrl, "publicBaseUrl"));
    }

    /** 파일을 쓴다. 같은 이름의 파일이 있으면 바꿔 쓴다. */
    public void write(String bundle, String fileName, byte[] content) {
        Objects.requireNonNull(content, "content");
        Path target = resolve(bundle, fileName);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "writing-", ".tmp");
            try {
                Files.write(temporary, content);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("파일을 저장하지 못했습니다: " + bundle + "/" + fileName, exception);
        }
    }

    /** 파일 내용. 없으면 빈 값. */
    public Optional<byte[]> read(String bundle, String fileName) {
        Path target = resolve(bundle, fileName);
        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (NoSuchFileException absent) {
            return Optional.empty();
        } catch (IOException exception) {
            throw new UncheckedIOException("파일을 읽지 못했습니다: " + bundle + "/" + fileName, exception);
        }
    }

    /** 파일을 지운다. 없으면 아무것도 하지 않는다. */
    public void delete(String bundle, String fileName) {
        Path target = resolve(bundle, fileName);
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new UncheckedIOException("파일을 지우지 못했습니다: " + bundle + "/" + fileName, exception);
        }
    }

    /** 누구나 그 파일을 여는 공개 주소. */
    public String publicUrlOf(String bundle, String fileName) {
        requireSegment(bundle);
        requireSegment(fileName);
        return publicBaseUrl + "/" + bundle + "/" + fileName;
    }

    private Path resolve(String bundle, String fileName) {
        requireSegment(bundle);
        requireSegment(fileName);
        Path target = rootDirectory.resolve(bundle).resolve(fileName).normalize();
        if (!target.startsWith(rootDirectory) || target.equals(rootDirectory)) {
            throw new IllegalArgumentException("저장 위치 밖의 경로입니다: " + bundle + "/" + fileName);
        }
        return target;
    }

    private static void requireSegment(String segment) {
        if (segment == null || !SEGMENT.matcher(segment).matches()) {
            throw new IllegalArgumentException("묶음 · 파일 이름은 URL 안전 문자와 확장자 하나로 된 한 덩어리여야 합니다: " + segment);
        }
    }

    private static String webAddress(String baseUrl) {
        String stripped = baseUrl.strip();
        URI uri = URI.create(stripped);
        boolean web = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
        if (!web || uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("공개 기준 주소는 쿼리 없는 http(s) 절대 주소여야 합니다: " + baseUrl);
        }
        return stripped.endsWith("/") ? stripped.substring(0, stripped.length() - 1) : stripped;
    }
}
