/**
 * 나가는 어댑터 · DB.
 * <p>
 * - 도메인별 하위 패키지에 application의 port/out을 구현하는 XxxPersistenceAdapter를 둔다.
 * - 표 ↔ 도메인 객체 변환은 이 모듈 안에서만 한다.
 * - jOOQ 코드는 src/main/resources/db/migration의 SQL에서 자동 생성된다 (com.ticketon.persistence.jooq).
 * - 읽기 전용 조회(현황·통계·정산 목록)는 도메인을 거치지 않고 바로 DTO로 돌려준다.
 */
package com.ticketon.persistence;
