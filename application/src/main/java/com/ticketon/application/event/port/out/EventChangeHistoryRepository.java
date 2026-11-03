package com.ticketon.application.event.port.out;

import com.ticketon.domain.event.EventChangeAuthor;
import com.ticketon.domain.event.EventChangeHistoryVersion;
import java.time.Instant;
import java.util.List;

/**
 * 제목 · 소개 · 포스터 · 회차 출연의 변경 이력 저장소 — 나가는 포트. 버전을 쌓고, 보관 기간이 지난 행사의 이력을 지운다(docs/03 "제목 · 소개 ·
 * 포스터는 변경 이력을 남깁니다", "보관은 행사가 끝난 날부터 6개월"). 무엇을 쌓고 언제 지우는지는 도메인({@code EventChangeHistory})이 정한다.
 * <p>
 * 버전은 덮어쓰지 않고 쌓는다 — 새 버전을 쌓으면 그 칸의 지금 버전은 그 순간에 유효 기간이 끝나고, 새 버전은 그 순간부터 다음 버전이 쌓일 때까지
 * 유효하다. 유효 기간은 [시작, 끝)이다.
 * <p>
 * <b>잠금 순서</b>(organizer 서비스의 {@code StaffAccountLocks} 표): 쌓는 흐름은 행사 줄을 FOR UPDATE로 잡은 채 부르고, 지우는 흐름은 먼저
 * {@link #lockEventForPurge}로 행사 줄만 FOR KEY SHARE로 잡는다 — 둘은 행사 줄에서 차례로 처리된다.
 */
public interface EventChangeHistoryRepository {

    /** 그 행사에 쌓인 버전이 하나라도 있는지 — 첫 버전을 두 번 쌓지 않으려고 본다. */
    boolean existsByEventId(long eventId);

    /**
     * 새 버전들을 쌓는다 — 버전마다 그 칸(회차 출연은 그 회차)의 지금 버전이 있으면 유효 끝을 그 순간으로 닫고, 그 순간부터 유효한 새 버전을 넣는다.
     * 통지 발송 여부는 '안 보냄'으로 넣는다(실제 값은 '예매자에게 알릴까요?' #110 · 출연자 변경 알림 #103이 채운다). 행사 줄을 FOR UPDATE로 잡은
     * 트랜잭션 안에서 부른다.
     *
     * @param versions  쌓을 버전 — 한 칸에 하나씩
     * @param author    바꾼 운영진(첫 버전은 판매를 시작한 운영진)
     * @param changedAt 바꾼 순간 — 새 버전의 유효 시작이자 지금 버전의 유효 끝
     * @throws IllegalArgumentException 같은 칸의 버전이 둘 이상이면
     */
    void add(long eventId, List<EventChangeHistoryVersion> versions, EventChangeAuthor author, Instant changedAt);

    /** 이력이 남아 있는 행사 id — 행사 id 차례. 잠그지 않고 읽으므로, 지우기 전에 {@link #lockEventForPurge}로 행사 줄을 잡고 다시 판단한다. */
    List<Long> findEventIdsWithHistory();

    /**
     * 이력을 지우기 전에 그 행사 줄을 이 트랜잭션이 끝날 때까지 FOR KEY SHARE로 잡는다 — 같은 행사의 편집(행사 줄 FOR UPDATE)이 진행 중이면 끝날 때까지
     * 기다렸다가, 그 뒤의 일정으로 보관 기간을 판단하고 그 사이에 쌓인 버전까지 지운다. 지운 행사(deleted_at)의 줄도 잡는다.
     * <p>
     * <b>잠금 순서 — 행사 줄만 잡는다.</b> 운영진 계정 줄 · 주최자 줄을 잡지 않고, 이 줄을 쥔 채 그 줄들을 기다리지도 않는다.
     *
     * @return 그 행사 줄이 있는지
     */
    boolean lockEventForPurge(long eventId);

    /**
     * 그 행사의 이력을 모두 지운다.
     *
     * @return 지운 버전 — 이력에만 남아 있던 포스터 파일을 함께 지우려고 돌려준다. 쌓은 차례
     */
    List<EventChangeHistoryVersion> deleteByEventId(long eventId);
}
