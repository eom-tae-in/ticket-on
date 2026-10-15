package com.ticketon.persistence.account;

import java.sql.SQLException;

/**
 * 유일 색인에 막힌 오류인지 알아본다 — UPDATE처럼 {@code ON CONFLICT}를 쓸 수 없는 문장이 겹치는 값을 쓰려다 PostgreSQL에 거부된 경우다
 * (SQLSTATE {@value #SQL_STATE}). jOOQ·Spring이 감싼 예외라도 원인 사슬을 따라가 본다.
 * <p>
 * 거부된 문장 뒤로 그 트랜잭션은 다른 조회를 받지 않으므로, 이 오류를 알아본 쪽은 다시 조회하지 않고 바로 충돌로 바꿔 알린다.
 */
final class UniqueViolation {

    static final String SQL_STATE = "23505";

    private UniqueViolation() {
    }

    static boolean isCauseOf(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
