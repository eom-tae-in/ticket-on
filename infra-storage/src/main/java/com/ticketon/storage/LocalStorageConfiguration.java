package com.ticketon.storage;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 로컬 파일 저장소 설정 — 저장 위치와 공개 기준 주소는 실행 모듈의 설정에서 읽는다(레포 안에 파일을 쓰지 않는다).
 * <ul>
 *   <li>{@code ticketon.storage.local.directory} — 저장 위치(환경 변수 LOCAL_STORAGE_DIRECTORY). 개발 기본값은 사용자 폴더 아래 개발용
 *       경로다.</li>
 *   <li>{@code ticketon.storage.local.public-base-url} — 공개 기준 주소(환경 변수 LOCAL_STORAGE_PUBLIC_BASE_URL). API 서버의 파일 공개 경로
 *       ({@code /api/public/files})를 가리킨다.</li>
 * </ul>
 * 운영 프로필은 기본값 없이 환경 변수를 꼭 받는다 — 운영 서버가 개발용 경로에 쓰거나 로컬 주소를 공개 주소로 내보내지 않게.
 */
@Configuration(proxyBeanMethods = false)
public class LocalStorageConfiguration {

    /**
     * @throws IllegalArgumentException 저장 위치가 비었으면 — 빈 경로는 서버를 띄운 폴더(레포 등)가 되므로 받지 않는다
     */
    @Bean
    public LocalFileStore localFileStore(@Value("${ticketon.storage.local.directory}") String directory,
            @Value("${ticketon.storage.local.public-base-url}") String publicBaseUrl) {
        if (directory.isBlank()) {
            throw new IllegalArgumentException("파일 저장 위치(ticketon.storage.local.directory)가 비어 있습니다");
        }
        return new LocalFileStore(Path.of(directory.strip()), publicBaseUrl);
    }
}
