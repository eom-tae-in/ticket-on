package com.ticketon.application.notification.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.application.notification.port.out.AudienceContactPort;
import com.ticketon.application.notification.port.out.EmailSendPort;
import com.ticketon.application.notification.port.out.NotificationSendRecordRepository;
import com.ticketon.application.notification.port.out.NotificationSettingsRepository;
import com.ticketon.application.notification.port.out.OrganizerStaffContactPort;
import com.ticketon.application.notification.port.out.SmsSendPort;
import com.ticketon.domain.notification.ContactPoint;
import com.ticketon.domain.notification.NotificationChannel;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.notification.NotificationSendRecord;
import com.ticketon.domain.notification.OrganizerRecipientScope;
import com.ticketon.domain.notification.SendResult;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 발송 — 받는 사람을 연락처로 바꾸고, 종류의 채널 표({@link NotificationKind#channelsReaching})대로 이메일·문자 포트를 부르고,
 * 수단마다 발송 이력을 남긴다. 수단·설정·받는 사람 범위는 도메인이 판단한다.
 * <p>
 * 보낼 곳을 모두 정한 뒤에 보내기 시작한다. 받는 사람이 맞지 않으면 한 통도 나가지 않는다.
 * <p>
 * 관객 계정으로 보낼 때 받을 곳이 없으면(탈퇴한 계정) 보내지 않고 이력도 남기지 않는다 — 탈퇴하면 연락처를 파기한다(docs/11).
 * 관객이 끈 묶음의 알림도 보내지 않는다.
 */
@Service
public class SendNotificationService implements SendNotificationUseCase {

    private final AudienceContactPort audienceContactPort;
    private final NotificationSettingsRepository settingsRepository;
    private final OrganizerStaffContactPort organizerStaffContactPort;
    private final EmailSendPort emailSendPort;
    private final SmsSendPort smsSendPort;
    private final NotificationSendRecordRepository sendRecordRepository;
    private final Clock clock;

    public SendNotificationService(AudienceContactPort audienceContactPort,
            NotificationSettingsRepository settingsRepository, OrganizerStaffContactPort organizerStaffContactPort,
            EmailSendPort emailSendPort, SmsSendPort smsSendPort, NotificationSendRecordRepository sendRecordRepository,
            Clock clock) {
        this.audienceContactPort = audienceContactPort;
        this.settingsRepository = settingsRepository;
        this.organizerStaffContactPort = organizerStaffContactPort;
        this.emailSendPort = emailSendPort;
        this.smsSendPort = smsSendPort;
        this.sendRecordRepository = sendRecordRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void send(SendNotificationCommand command) {
        Instant now = clock.instant();
        NotificationKind kind = command.kind();
        List<Delivery> deliveries = contactsOf(kind, command.recipient()).stream()
                .flatMap(contact -> kind.channelsReaching(contact).stream().map(channel -> new Delivery(contact, channel)))
                .toList();
        for (Delivery delivery : deliveries) {
            deliver(kind, command.content(), delivery, now);
        }
    }

    private List<ContactPoint> contactsOf(NotificationKind kind, NotificationRecipient recipient) {
        if (!kind.canBeSentTo(recipient)) {
            throw new IllegalArgumentException(kind + " 알림은 이 받는 사람에게 보낼 수 없습니다: " + recipient);
        }
        return switch (recipient) {
            case NotificationRecipient.AudienceMember member -> audienceContactReceiving(kind, member.audienceAccountId());
            case NotificationRecipient.DirectContact directContact -> List.of(directContact.contactPoint());
            case NotificationRecipient.Organizer organizer -> organizerStaffContacts(kind, organizer.organizerId());
        };
    }

    /** 끈 묶음의 알림이거나 탈퇴한 계정이면 비어 있다. */
    private List<ContactPoint> audienceContactReceiving(NotificationKind kind, long audienceAccountId) {
        if (!settingsRepository.findByAudienceAccountId(audienceAccountId).receives(kind)) {
            return List.of();
        }
        return audienceContactPort.findContact(audienceAccountId).stream().toList();
    }

    private List<ContactPoint> organizerStaffContacts(NotificationKind kind, long organizerId) {
        OrganizerRecipientScope scope = kind.organizerRecipientScope()
                .orElseThrow(() -> new IllegalStateException("주최자 쪽 종류에 받는 사람 범위가 없습니다: " + kind));
        return organizerStaffContactPort.findStaffContacts(organizerId)
                .orElseThrow(() -> new IllegalArgumentException("없는 주최자에게 알림을 보낼 수 없습니다: " + organizerId))
                .emailsFor(scope).stream()
                .map(ContactPoint::emailOnly)
                .toList();
    }

    private void deliver(NotificationKind kind, NotificationContent content, Delivery delivery, Instant now) {
        ContactPoint contact = delivery.contact();
        SendResult result = switch (delivery.channel()) {
            case EMAIL -> emailSendPort.send(content.emailTo(contact.email().orElseThrow(), kind.side()));
            case SMS -> smsSendPort.send(content.smsTo(contact.phoneNumber().orElseThrow()));
        };
        sendRecordRepository.add(new NotificationSendRecord(kind, delivery.channel(),
                contact.addressFor(delivery.channel()), result, now));
    }

    /** 한 연락처에 한 수단으로 보낼 한 통. */
    private record Delivery(ContactPoint contact, NotificationChannel channel) {
    }
}
