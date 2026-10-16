package com.ticketon.application.event.port.out;

import com.ticketon.domain.event.EventCategoryCatalog;

/**
 * 정식 카테고리 저장소 — 나가는 포트.
 * <p>
 * 카테고리 목록은 enum이 아니라 데이터다 — 관리자가 '기타'의 자유 입력 장르명 가운데 자주 나오는 것을 정식 카테고리로 승격하면 늘어난다
 * (docs/03 "카테고리", docs/13). 처음 목록은 docs/03 표 그대로 표에 넣어 둔다({@link EventCategoryCatalog#initial}과 같다). 승격(관리자 기능)은
 * 따로 만든다.
 */
public interface EventCategoryRepository {

    /** 지금 정식 카테고리 목록 전체 — 유형마다 보여 줄 순서대로. "기타"는 담기지 않는다. */
    EventCategoryCatalog loadCatalog();
}
