package com.ticketon.storage;

import com.ticketon.application.organizer.port.out.OrganizerLogoStoragePort;
import com.ticketon.domain.organizer.OrganizerLogo;
import com.ticketon.domain.organizer.OrganizerLogoImage;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 주최자 로고 파일 — 로컬 디스크 어댑터. 파일은 {@link LocalFileStore}의 {@value #BUNDLE} 묶음에 로고 열쇠를 파일 이름으로 둔다.
 * <p>
 * 공개 주소는 {@code 공개 기준 주소/organizer-logos/열쇠}이고, API 서버가 그 경로({@code /api/public/files/organizer-logos/{열쇠}})로 들어온
 * 요청에 이 어댑터가 읽은 파일을 내준다. 업체 어댑터를 더하면 공개 주소는 업체의 주소가 된다.
 */
@Component
public class LocalOrganizerLogoStorageAdapter implements OrganizerLogoStoragePort {

    /** 로고 파일을 두는 묶음 — 공개 주소에도 그대로 들어간다. */
    static final String BUNDLE = "organizer-logos";

    private final LocalFileStore fileStore;

    public LocalOrganizerLogoStorageAdapter(LocalFileStore fileStore) {
        this.fileStore = fileStore;
    }

    @Override
    public void store(OrganizerLogo logo, OrganizerLogoImage image) {
        fileStore.write(BUNDLE, logo.fileKey(), image.content());
    }

    @Override
    public void delete(OrganizerLogo logo) {
        fileStore.delete(BUNDLE, logo.fileKey());
    }

    @Override
    public String publicUrlOf(OrganizerLogo logo) {
        return fileStore.publicUrlOf(BUNDLE, logo.fileKey());
    }

    @Override
    public Optional<byte[]> read(OrganizerLogo logo) {
        return fileStore.read(BUNDLE, logo.fileKey());
    }
}
