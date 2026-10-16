package com.ticketon.domain.event;

import java.util.Objects;

/**
 * 장소 — 공연장 이름과 주소. 둘 다 필수다(docs/03 "기본 정보", 위저드 1단계 "장소").
 * <p>
 * <b>장소는 행사에 하나다</b> — 일부 회차만 다른 곳으로 옮길 수 없다(docs/04 "장소 변경"). 예매가 열린 뒤에는 주최자 혼자 바꿀 수 없고 관리자
 * 승인을 거친다 — 홍대 소극장인 줄 알고 샀는데 강남으로 바뀌면 사실상 계약 내용이 바뀐 것이기 때문이다(docs/04 "예매가 열린 뒤 수정 범위").
 * 상세 안내("지하 1층", "주차 불가")와 지도 링크는 장소와 따로 갖는다({@link EventBasicInformation}).
 * <p>
 * 둘 다 앞뒤 공백을 걷고 적은 그대로 둔다. 주소의 형식과 길이는 문서에 없어 정하지 않았다 — 지도 API를 붙이지 않고(docs/03, docs/99 B
 * "지도 API 임베드") 주소를 누르면 외부 지도로 넘어가는 정도라 주소를 해석할 일이 없다.
 *
 * @param name    공연장 이름 — 예: "○○아트홀"
 * @param address 주소
 */
public record Venue(String name, String address) {

    public Venue {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
        name = name.strip();
        address = address.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("공연장 이름이 비어 있습니다");
        }
        if (address.isEmpty()) {
            throw new IllegalArgumentException("주소가 비어 있습니다");
        }
    }
}
