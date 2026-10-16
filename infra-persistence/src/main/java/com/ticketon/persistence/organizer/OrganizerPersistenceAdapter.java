package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER;
import static com.ticketon.persistence.jooq.Tables.ORGANIZER_SNS_LINK;

import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerBusinessDetails;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerLogo;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerPublicProfile;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.persistence.jooq.tables.records.OrganizerRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.ResultQuery;
import org.jooq.SelectConditionStep;
import org.jooq.UpdateSetMoreStep;
import org.jooq.UpdateSetStep;
import org.springframework.stereotype.Component;

/**
 * 주최자 저장소(organizer — V8, 공개 정보 V16, 사업자 정보 · 정산 계좌 V18) 어댑터.
 * <p>
 * 이름 · 상태와 공개 정보(소개 · 로고 열쇠, SNS 링크는 organizer_sns_link)를 저장한다. 새 주최자는 심사 중으로만 만들어지고
 * ({@link Organizer#underReview}) 공개 정보가 비어 있다. 사업자 여부 · 사업자 정보 · 정산 계좌는 같은 줄에 있지만 {@link Organizer}에 싣지
 * 않고 따로 읽고 쓴다({@link #findBusinessDetails}) — 승인할 때 함께 적고, 그 뒤로는 관리자가 승인한 변경만 적는다.
 * <p>
 * 승인 저장은 {@code UPDATE … WHERE status = 'UNDER_REVIEW'}로 한다. 바뀐 줄이 없으면 읽은 뒤 다른 처리가 먼저 상태를 바꾼 것이라 충돌이다.
 * 공개 정보 편집과 주최자명 변경은 {@link #findByIdForUpdate}({@code SELECT … FOR UPDATE})로 줄을 잠그고 읽은 뒤 저장하므로, 저장은 조건
 * 없이 그 주최자의 공개 정보 칸 · 이름 칸만 바꾼다. 로고 열쇠는 유일 칸이라 로고를 바꾸는 저장은 줄을 FOR UPDATE와 같은 세기로 잠근다 — 이
 * 주최자를 가리키는 줄을 넣는 외래 키 확인 잠금(FOR KEY SHARE)과 부딪힌다. 그래서 줄을 잠그는 흐름은 운영진 계정 줄을 먼저 잡는다(잠금 순서 —
 * application의 StaffAccountLocks).
 */
@Component
public class OrganizerPersistenceAdapter implements OrganizerRepository {

    private final DSLContext dsl;

    public OrganizerPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Organizer add(Organizer newOrganizer) {
        if (newOrganizer.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 주최자입니다: " + newOrganizer.id().getAsLong());
        }
        OrganizerPublicProfile profile = newOrganizer.publicProfile();
        long id = dsl.insertInto(ORGANIZER)
                .set(ORGANIZER.NAME, newOrganizer.name().value())
                .set(ORGANIZER.STATUS, newOrganizer.status().name())
                .set(ORGANIZER.INTRODUCTION, profile.introduction())
                .set(ORGANIZER.LOGO_FILE_KEY, logoFileKeyOf(profile))
                .returningResult(ORGANIZER.ID)
                .fetchSingle()
                .value1();
        insertSnsLinks(id, profile.snsLinks());
        return Organizer.restore(id, newOrganizer.name(), newOrganizer.status(), profile);
    }

    @Override
    public Optional<Organizer> findById(long id) {
        return find(organizerRow(id));
    }

    @Override
    public Optional<Organizer> findByIdForUpdate(long id) {
        return find(organizerRow(id).forUpdate());
    }

    @Override
    public void saveApproval(Organizer approvedOrganizer, OrganizerBusinessDetails businessDetails) {
        long id = approvedOrganizer.id()
                .orElseThrow(() -> new IllegalArgumentException("저장 전 주최자는 승인을 저장할 수 없습니다"));
        if (approvedOrganizer.status() != OrganizerStatus.ACTIVE) {
            throw new IllegalArgumentException("승인한 주최자가 아닙니다: 주최자 " + id + ", 상태 " + approvedOrganizer.status());
        }
        int updated = withBusinessDetails(dsl.update(ORGANIZER).set(ORGANIZER.STATUS, OrganizerStatus.ACTIVE.name()),
                businessDetails)
                .where(ORGANIZER.ID.eq(id))
                .and(ORGANIZER.STATUS.eq(OrganizerStatus.UNDER_REVIEW.name()))
                .execute();
        if (updated == 0) {
            throw new ConflictException(OrganizerErrorCode.ORGANIZER_NOT_UNDER_REVIEW,
                    "승인 경합 — 주최자 " + id + "가 이미 심사 중이 아님");
        }
    }

    @Override
    public Optional<OrganizerBusinessDetails> findBusinessDetails(long organizerId) {
        return dsl.selectFrom(ORGANIZER)
                .where(ORGANIZER.ID.eq(organizerId))
                .and(ORGANIZER.BUSINESS_STATUS.isNotNull())
                .fetchOptional()
                .map(OrganizerPersistenceAdapter::toBusinessDetails);
    }

    @Override
    public void saveBusinessDetails(long organizerId, OrganizerBusinessDetails businessDetails) {
        int updated = withBusinessDetails(dsl.update(ORGANIZER), businessDetails)
                .where(ORGANIZER.ID.eq(organizerId))
                .execute();
        if (updated == 0) {
            throw new IllegalArgumentException("표에 없는 주최자의 사업자 정보 · 정산 계좌를 저장하려 했습니다: " + organizerId);
        }
    }

    @Override
    public void savePublicProfile(Organizer organizer) {
        long id = organizer.id()
                .orElseThrow(() -> new IllegalArgumentException("저장 전 주최자는 공개 정보를 저장할 수 없습니다"));
        OrganizerPublicProfile profile = organizer.publicProfile();
        int updated = dsl.update(ORGANIZER)
                .set(ORGANIZER.INTRODUCTION, profile.introduction())
                .set(ORGANIZER.LOGO_FILE_KEY, logoFileKeyOf(profile))
                .where(ORGANIZER.ID.eq(id))
                .execute();
        if (updated == 0) {
            throw new IllegalArgumentException("표에 없는 주최자의 공개 정보를 저장하려 했습니다: " + id);
        }
        dsl.deleteFrom(ORGANIZER_SNS_LINK).where(ORGANIZER_SNS_LINK.ORGANIZER_ID.eq(id)).execute();
        insertSnsLinks(id, profile.snsLinks());
    }

    @Override
    public void saveName(Organizer renamedOrganizer) {
        long id = renamedOrganizer.id()
                .orElseThrow(() -> new IllegalArgumentException("저장 전 주최자는 이름을 저장할 수 없습니다"));
        int updated = dsl.update(ORGANIZER)
                .set(ORGANIZER.NAME, renamedOrganizer.name().value())
                .where(ORGANIZER.ID.eq(id))
                .execute();
        if (updated == 0) {
            throw new IllegalArgumentException("표에 없는 주최자의 이름을 저장하려 했습니다: " + id);
        }
    }

    /** 사업자 여부 · 사업자 정보 다섯 칸 · 정산 계좌 세 칸을 고치는 갱신 — 비사업자면 사업자 정보 칸을 비운다. */
    private static UpdateSetMoreStep<OrganizerRecord> withBusinessDetails(UpdateSetStep<OrganizerRecord> update,
            OrganizerBusinessDetails businessDetails) {
        Optional<BusinessInformation> business = businessDetails.businessInformation();
        SettlementAccount settlementAccount = businessDetails.settlementAccount();
        return update
                .set(ORGANIZER.BUSINESS_STATUS, businessDetails.businessStatus().name())
                .set(ORGANIZER.BUSINESS_TRADE_NAME, business.map(BusinessInformation::tradeName).orElse(null))
                .set(ORGANIZER.BUSINESS_REPRESENTATIVE_NAME,
                        business.map(BusinessInformation::representativeName).orElse(null))
                .set(ORGANIZER.BUSINESS_ADDRESS, business.map(BusinessInformation::businessAddress).orElse(null))
                .set(ORGANIZER.BUSINESS_PHONE_NUMBER,
                        business.map(information -> information.businessPhoneNumber().value()).orElse(null))
                .set(ORGANIZER.BUSINESS_REGISTRATION_NUMBER,
                        business.map(information -> information.registrationNumber().digits()).orElse(null))
                .set(ORGANIZER.SETTLEMENT_BANK_NAME, settlementAccount.bankName())
                .set(ORGANIZER.SETTLEMENT_ACCOUNT_NUMBER, settlementAccount.accountNumber())
                .set(ORGANIZER.SETTLEMENT_ACCOUNT_HOLDER, settlementAccount.accountHolder());
    }

    private static OrganizerBusinessDetails toBusinessDetails(OrganizerRecord row) {
        return new OrganizerBusinessDetails(
                BusinessColumns.declarationOf(row.getBusinessStatus(), row.getBusinessTradeName(),
                        row.getBusinessRepresentativeName(), row.getBusinessAddress(), row.getBusinessPhoneNumber(),
                        row.getBusinessRegistrationNumber()),
                BusinessColumns.settlementAccountOf(row.getSettlementBankName(), row.getSettlementAccountNumber(),
                        row.getSettlementAccountHolder()));
    }

    private static String logoFileKeyOf(OrganizerPublicProfile profile) {
        return profile.hasLogo() ? profile.logo().fileKey() : null;
    }

    private SelectConditionStep<OrganizerRecord> organizerRow(long id) {
        return dsl.selectFrom(ORGANIZER).where(ORGANIZER.ID.eq(id));
    }

    private Optional<Organizer> find(ResultQuery<OrganizerRecord> query) {
        return query.fetchOptional().map(this::toOrganizer);
    }

    private Organizer toOrganizer(OrganizerRecord row) {
        OrganizerLogo logo = row.getLogoFileKey() == null ? null : new OrganizerLogo(row.getLogoFileKey());
        OrganizerPublicProfile publicProfile = new OrganizerPublicProfile(row.getIntroduction(), snsLinksOf(row.getId()),
                logo);
        return Organizer.restore(row.getId(), new OrganizerName(row.getName()), OrganizerStatus.valueOf(row.getStatus()),
                publicProfile);
    }

    private List<ExternalLink> snsLinksOf(long organizerId) {
        return dsl.select(ORGANIZER_SNS_LINK.URL)
                .from(ORGANIZER_SNS_LINK)
                .where(ORGANIZER_SNS_LINK.ORGANIZER_ID.eq(organizerId))
                .orderBy(ORGANIZER_SNS_LINK.DISPLAY_ORDER)
                .fetch(Record1::value1)
                .stream()
                .map(ExternalLink::new)
                .toList();
    }

    private void insertSnsLinks(long organizerId, List<ExternalLink> links) {
        for (int displayOrder = 0; displayOrder < links.size(); displayOrder++) {
            dsl.insertInto(ORGANIZER_SNS_LINK)
                    .set(ORGANIZER_SNS_LINK.ORGANIZER_ID, organizerId)
                    .set(ORGANIZER_SNS_LINK.DISPLAY_ORDER, displayOrder)
                    .set(ORGANIZER_SNS_LINK.URL, links.get(displayOrder).value())
                    .execute();
        }
    }
}
