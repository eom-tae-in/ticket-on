package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.in.GetOrganizerLogoFileQuery;
import com.ticketon.application.organizer.port.in.GetOrganizerLogoFileUseCase;
import com.ticketon.application.organizer.port.in.OrganizerLogoFileResult;
import com.ticketon.application.organizer.port.out.OrganizerLogoStoragePort;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerLogo;
import org.springframework.stereotype.Service;

/**
 * 주최자 로고 파일 내주기 — 들어온 열쇠가 로고 열쇠의 꼴인지({@link OrganizerLogo#isValidFileKey}) 먼저 보고, 맞을 때만 파일 저장소에서 읽는다.
 * 경로를 거스르는 이름이나 다른 꼴의 값은 저장소에 닿지 않는다. 형식(Content-Type)은 열쇠의 확장자로 정한다 — 저장할 때 실제 바이트의 형식으로
 * 붙인 확장자다. DB를 읽지 않아 트랜잭션을 열지 않는다.
 */
@Service
public class GetOrganizerLogoFileService implements GetOrganizerLogoFileUseCase {

    private final OrganizerLogoStoragePort logoStorage;

    public GetOrganizerLogoFileService(OrganizerLogoStoragePort logoStorage) {
        this.logoStorage = logoStorage;
    }

    @Override
    public OrganizerLogoFileResult get(GetOrganizerLogoFileQuery query) {
        if (!OrganizerLogo.isValidFileKey(query.fileKey())) {
            throw notFound(query.fileKey());
        }
        OrganizerLogo logo = new OrganizerLogo(query.fileKey());
        byte[] content = logoStorage.read(logo).orElseThrow(() -> notFound(query.fileKey()));
        return new OrganizerLogoFileResult(content, logo.format().mediaType());
    }

    private static NotFoundException notFound(String fileKey) {
        return new NotFoundException(OrganizerErrorCode.ORGANIZER_LOGO_NOT_FOUND, "로고 열쇠 " + fileKey);
    }
}
