package com.ticketon.application.notification.port.out;

import com.ticketon.domain.notification.NotificationPreference;
import com.ticketon.domain.notification.NotificationSettings;

/**
 * 관객 알림 수신 설정 저장소 — 나가는 포트.
 * <p>
 * 관객이 바꾼 묶음만 저장한다. 바꾼 적 없는 묶음은 기본값을 따른다({@link NotificationSettings#restore}).
 */
public interface NotificationSettingsRepository {

    /** 그 계정의 수신 설정. 바꾼 적이 없으면 기본값 그대로다. */
    NotificationSettings findByAudienceAccountId(long audienceAccountId);

    /**
     * 묶음 하나의 선택을 저장한다. 다른 묶음의 줄은 건드리지 않는다 — 두 묶음을 거의 동시에 바꿔도 서로 덮어쓰지 않게.
     *
     * @param audienceAccountId 저장된 관객 계정
     */
    void saveChoice(long audienceAccountId, NotificationPreference preference, boolean receives);
}
