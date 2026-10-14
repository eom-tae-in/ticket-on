package com.ticketon.domain.account;

/**
 * 가입하려는 전화번호를 쓸 수 있는지 — 저장소가 확인해 가입 규칙({@link AudienceAccount#signUp})에 넘긴다.
 * <p>
 * <b>번호 하나에 계정 하나</b>다(docs/00 "전제", docs/01 "전화번호", docs/99 C). 관객이 본인 번호를 적는다는 전제 위에서
 * 1인 매수 제한과 관객 이용 정지가 사실상 사람 단위로 걸리는 근거다. OAuth로 가입해도 똑같이 확인한다(docs/01 "OAuth는 중복 계정을 막지 못합니다").
 */
public enum PhoneNumberAvailability {

    /** 그 번호로 가입한 계정이 없고 차단 목록에도 없다. */
    AVAILABLE,

    /** 같은 번호로 가입한 계정이 있다. */
    ALREADY_REGISTERED,

    /** 이용 정지된 계정이 탈퇴하며 차단 목록에 남긴 번호다(docs/01 "관객 이용 정지"). 다시 가입할 수 없다. */
    BLOCKED
}
