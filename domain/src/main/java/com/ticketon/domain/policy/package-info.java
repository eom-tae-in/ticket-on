/**
 * 정책 숫자 모음 — docs/99-decisions.md C "확정된 숫자"를 코드 한곳에 모은다.
 * <p>
 * 문서의 숫자(시간·수량·비율·보관 기간)를 코드에 상수로 흩뿌리지 않고 여기서 가져다 쓴다
 * (.claude/rules/planning-docs.md). 99 C의 표를 그대로 따라 나눈다:
 * {@link com.ticketon.domain.policy.TimePolicy 시간},
 * {@link com.ticketon.domain.policy.QuantityPolicy 수량·비율},
 * {@link com.ticketon.domain.policy.RecordRetentionPolicy 보관 기간}.
 * 상수마다 근거 문서 위치를 한 줄로 단다. 값을 바꾸려면 99 C를 먼저 바꾸고 여기를 맞춘다.
 * <p>
 * 이 패키지는 도메인 패키지의 층(⓪~⑤, ArchitectureTest) 밖에 있다. 모든 도메인 패키지와 application이 쓸 수 있고,
 * 이 패키지는 다른 도메인 패키지를 부르지 않는다. 숫자만 두고, 숫자를 쓰는 규칙(판정·계산)은 각 도메인 객체가 가진다.
 * <p>
 * 값은 값 객체로 둔다: 시각 단위(분·시간)는 {@link java.time.Duration}, 날짜 단위(일·주·월·년)는 {@link java.time.Period},
 * 영업일은 {@link com.ticketon.domain.policy.BusinessDays}, 비율은 {@link com.ticketon.domain.policy.Percent},
 * "N개 또는 전체의 P%" 기준선은 {@link com.ticketon.domain.policy.QuantityThreshold},
 * 가로세로 비율은 {@link com.ticketon.domain.policy.AspectRatio}, 파일 용량은 {@link com.ticketon.domain.policy.FileSize}. 날짜·시각은 한국 시각 기준이다(common의 KoreanTime).
 * <p>
 * 이름이 "기본값"인 상수(예매 종료·검표 시작·1회 선택 매수·묶음권 할인율·수정 요청 기한)는 주최자나 관리자가 바꿀 수 있는 값의
 * 처음 채워 두는 값이다. 바뀐 값은 행사·요청마다 저장하고, 규칙은 저장된 값을 쓴다.
 *
 * <h2>문서에 없는 값</h2>
 * 구현 중 문서에 없는 기본값·기술값(링크 유효 시간, 업로드 용량 상한, 배치 주기 등)이 필요하면 임의로 확정하지 않는다.
 * <ol>
 *   <li>그 이슈에서 사용자에게 질문으로 올린다.</li>
 *   <li>답을 기다리는 동안 {@link com.ticketon.domain.policy.ProposedPolicy}('확인 필요' 구역)에 제안값으로 추가한다.
 *       상수마다 "확인 필요", 제안한 이슈 번호, 그 값을 제안한 이유를 적는다.</li>
 *   <li>#135에서 사용자가 확정하면 docs/99 C에 기록하고, 값을 위 세 클래스 중 맞는 곳으로 옮긴 뒤 '확인 필요' 구역에서 지운다.</li>
 * </ol>
 * 확정된 세 클래스에는 99 C에 있는 값만 둔다.
 */
package com.ticketon.domain.policy;
