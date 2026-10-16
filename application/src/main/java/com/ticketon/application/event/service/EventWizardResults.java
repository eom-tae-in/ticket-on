package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.domain.event.BookingSettings;
import com.ticketon.domain.event.CancellationPolicy;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventBasicInformation;
import com.ticketon.domain.event.EventVisibilitySetting;
import com.ticketon.domain.event.TicketsPerPersonLimit;

/** 행사를 위저드 값(Result)으로 옮긴다 — 도메인 객체를 포트 밖으로 그대로 내보내지 않는다. */
final class EventWizardResults {

    private EventWizardResults() {
    }

    static EventWizardResult of(Event event) {
        return new EventWizardResult(event.id().orElseThrow(), event.status(), event.wizardProgress(), event.lastModifiedAt(),
                event.publicAddress().value(), basicInformationOf(event.basicInformation()),
                bookingStepOf(event.visibility(), event.bookingSettings(), event.cancellationPolicy()));
    }

    private static EventWizardResult.BasicInformation basicInformationOf(EventBasicInformation information) {
        return new EventWizardResult.BasicInformation(information.title().value(), information.type(),
                information.category().categoryName(),
                information.category().isOther() ? information.category().genreName().value() : null,
                information.introduction(), information.venue().name(), information.venue().address(),
                information.venueDetails(), information.mapLink() == null ? null : information.mapLink().value(),
                information.publicContact() == null ? null : information.publicContact().value());
    }

    private static EventWizardResult.BookingStep bookingStepOf(EventVisibilitySetting visibility, BookingSettings settings,
            CancellationPolicy cancellationPolicy) {
        TicketsPerPersonLimit.Limited ticketsPerPerson = settings.ticketsPerPerson() instanceof TicketsPerPersonLimit.Limited limited
                ? limited : null;
        return new EventWizardResult.BookingStep(visibility.visibility(), visibility.passwordHash() != null,
                settings.bookingOpensAt(), settings.bookingCloseLeadTime().duration(), settings.checkinOpenLeadTime().duration(),
                settings.seatsPerSelection().maxSeats(), ticketsPerPerson == null ? null : ticketsPerPerson.maxTickets(),
                ticketsPerPerson == null ? null : ticketsPerPerson.basis(), settings.sessionChoice(), settings.reentry().isAllowed(),
                settings.ageRestriction().isRestricted() ? settings.ageRestriction().minimumAge() : null,
                cancellationPolicy.tiers().stream()
                        .map(tier -> new EventWizardResult.CancellationFeeTier(tier.fromDaysBefore(), tier.deductionRate().value()))
                        .toList(),
                cancellationPolicy.isStandard());
    }
}
