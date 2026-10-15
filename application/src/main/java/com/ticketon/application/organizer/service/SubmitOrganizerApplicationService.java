package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.in.BusinessItems;
import com.ticketon.application.organizer.port.in.SubmitOrganizerApplicationCommand;
import com.ticketon.application.organizer.port.in.SubmitOrganizerApplicationResult;
import com.ticketon.application.organizer.port.in.SubmitOrganizerApplicationUseCase;
import com.ticketon.application.organizer.port.out.OrganizerApplicationRepository;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.organizer.BusinessDeclaration;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerApplication;
import com.ticketon.domain.organizer.OrganizerApplicationForm;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주최자 신청서 제출 — 신청 항목 규칙은 {@link BusinessDeclaration#declare}·{@link OrganizerApplicationForm}이, 낼 수 있는지는
 * {@link OrganizerApplication#requireCanSubmit}이 본다. 여기서는 저장소에 심사 중 신청이 있는지 확인해 넘기고, 새 주최자를 심사 중으로
 * 만들어 저장한 뒤 그 주최자를 가리키는 신청서를 저장한다.
 * <p>
 * 주최자와 신청서는 한 트랜잭션이다 — 같은 사람의 동시 신청에 져 신청서를 저장하지 못하면 함께 만든 주최자도 되돌려진다.
 * 신청한 사람을 그 주최자에 소속시키지 않는다 — 승인 때 소유자가 된다(#23).
 */
@Service
public class SubmitOrganizerApplicationService implements SubmitOrganizerApplicationUseCase {

    private final OrganizerRepository organizerRepository;
    private final OrganizerApplicationRepository applicationRepository;
    private final Clock clock;

    public SubmitOrganizerApplicationService(OrganizerRepository organizerRepository,
            OrganizerApplicationRepository applicationRepository, Clock clock) {
        this.organizerRepository = organizerRepository;
        this.applicationRepository = applicationRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SubmitOrganizerApplicationResult submit(SubmitOrganizerApplicationCommand command) {
        Instant now = clock.instant();
        long applicantStaffAccountId = command.account().id();
        OrganizerApplicationForm form = formOf(command);
        OrganizerApplication.requireCanSubmit(applicationRepository.reviewStateOf(applicantStaffAccountId));

        Organizer organizer = organizerRepository.add(Organizer.underReview(form.name()));
        long organizerId = organizer.id().orElseThrow(() -> new IllegalStateException("저장한 주최자에 id가 없습니다"));
        OrganizerApplication application = applicationRepository.add(
                OrganizerApplication.submit(organizerId, applicantStaffAccountId, form, now));
        long applicationId = application.id().orElseThrow(() -> new IllegalStateException("저장한 신청서에 id가 없습니다"));
        return new SubmitOrganizerApplicationResult(applicationId, form.name().value(), application.submittedAt());
    }

    private static OrganizerApplicationForm formOf(SubmitOrganizerApplicationCommand command) {
        BusinessItems businessItems = command.businessItems();
        BusinessDeclaration businessDeclaration = BusinessDeclaration.declare(command.businessStatus(),
                businessItems.tradeName(), businessItems.representativeName(), businessItems.businessAddress(),
                businessItems.businessPhoneNumber(), businessItems.businessRegistrationNumber());
        return new OrganizerApplicationForm(command.organizerType(), command.name(), command.contactPhoneNumber(),
                command.email(), command.websiteAndSnsLinks(), command.eventHistoryOrPlan(), businessDeclaration,
                command.settlementAccount());
    }
}
