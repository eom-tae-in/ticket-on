package com.ticketon.application.notification.port.out;

import com.ticketon.domain.notification.InboxNotification;
import java.util.List;

/**
 * 관객 알림함 저장소 — 나가는 포트.
 * <p>
 * 관객 계정에 보낸 알림을 한 건에 한 줄씩 쌓는다(docs/12 "전달 수단과 알림함"). 보관 기간은 아직 정하지 않았다(#32).
 */
public interface NotificationInboxRepository {

    /** 저장 전 한 줄을 쌓는다. */
    void add(InboxNotification notification);

    /** 그 계정의 알림함 전부, 최신순(보낸 시각이 늦은 것부터, 같으면 나중에 쌓인 것부터). 비어 있을 수 있다. */
    List<InboxNotification> findByAudienceAccountId(long audienceAccountId);

    /**
     * 처음 본 시각을 저장한다. 이미 처음 본 시각이 저장된 줄은 그대로 둔다 — 같은 알림함을 거의 동시에 두 번 열어도 먼저 연 시각이 남는다.
     *
     * @param notifications 저장된 줄({@link InboxNotification#id()}가 있음) 가운데 처음 본 시각이 채워진 것
     */
    void saveFirstSeen(List<InboxNotification> notifications);
}
