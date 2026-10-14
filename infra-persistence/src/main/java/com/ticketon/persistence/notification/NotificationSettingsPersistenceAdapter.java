package com.ticketon.persistence.notification;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_NOTIFICATION_PREFERENCE;

import com.ticketon.application.notification.port.out.NotificationSettingsRepository;
import com.ticketon.domain.notification.NotificationPreference;
import com.ticketon.domain.notification.NotificationSettings;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 관객 알림 수신 설정 저장소(audience_notification_preference — V3) 어댑터.
 * <p>
 * 관객이 바꾼 적 있는 묶음만 줄이 있다. 바꿀 때는 그 묶음의 줄만 {@code INSERT … ON CONFLICT DO UPDATE}로 고친다 — 두 묶음을
 * 거의 동시에 바꿔도 한쪽이 다른 쪽을 옛 값으로 덮지 않는다.
 */
@Component
public class NotificationSettingsPersistenceAdapter implements NotificationSettingsRepository {

    private final DSLContext dsl;

    public NotificationSettingsPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public NotificationSettings findByAudienceAccountId(long audienceAccountId) {
        Map<NotificationPreference, Boolean> storedChoices = dsl.selectFrom(AUDIENCE_NOTIFICATION_PREFERENCE)
                .where(AUDIENCE_NOTIFICATION_PREFERENCE.AUDIENCE_ACCOUNT_ID.eq(audienceAccountId))
                .fetchMap(choice -> NotificationPreference.valueOf(choice.getPreference()),
                        choice -> choice.getReceives());
        return NotificationSettings.restore(audienceAccountId, storedChoices);
    }

    @Override
    public void saveChoice(long audienceAccountId, NotificationPreference preference, boolean receives) {
        dsl.insertInto(AUDIENCE_NOTIFICATION_PREFERENCE)
                .set(AUDIENCE_NOTIFICATION_PREFERENCE.AUDIENCE_ACCOUNT_ID, audienceAccountId)
                .set(AUDIENCE_NOTIFICATION_PREFERENCE.PREFERENCE, preference.name())
                .set(AUDIENCE_NOTIFICATION_PREFERENCE.RECEIVES, receives)
                .onConflict(AUDIENCE_NOTIFICATION_PREFERENCE.AUDIENCE_ACCOUNT_ID, AUDIENCE_NOTIFICATION_PREFERENCE.PREFERENCE)
                .doUpdate()
                .set(AUDIENCE_NOTIFICATION_PREFERENCE.RECEIVES, receives)
                .execute();
    }
}
