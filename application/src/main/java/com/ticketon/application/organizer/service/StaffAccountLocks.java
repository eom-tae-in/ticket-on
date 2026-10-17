package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.domain.account.StaffAccount;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.stream.LongStream;

/**
 * 운영진 계정 줄 잠금과 <b>주최 관리 · 관리자 흐름 전체의 잠금 순서</b>. 관리자 기능(admin)도 이것을 쓴다.
 * <p>
 * 한 트랜잭션이 줄을 쥔 채 다른 줄을 기다리는데 다른 트랜잭션이 그 반대로 쥐고 기다리면, PostgreSQL이 한쪽을 교착(40P01)으로 끊는다. 그래서
 * 줄을 잠그는 흐름은 모두 아래 차례로만 잠근다 — 앞 차례의 줄을 쥔 채 뒤 차례의 줄을 기다릴 수는 있어도, 뒤 차례의 줄을 쥔 채 앞 차례의 줄을
 * 기다리지 않는다.
 * <table>
 *   <caption>잠금 순서 — 위에서 아래로</caption>
 *   <tr><th>차례</th><th>어떻게 잠그나</th><th>흐름</th></tr>
 *   <tr><td>① 운영진 계정 줄 — FOR UPDATE</td>
 *       <td>관련된 사람 모두를 id가 작은 쪽부터, 무엇이든 바꾸기 전에 한 번에({@link #lockInIdOrder} — 한 사람이면
 *           {@link StaffAccountRepository#findByIdForUpdate}, 비밀번호 재설정 링크 저장소의 계정 잠금도 같은 줄이다). 그 사람의 소속 · 계정을
 *           바꾸는 흐름이다.</td>
 *       <td>초대 수락 · 거절, 소유자 넘기기, 운영자 내보내기, 나가기, 소유자 이전 신청, 주최자 신청 승인, 소유자 이전 신청 승인, 운영진 로그인
 *           탈퇴</td></tr>
 *   <tr><td>① 운영진 계정 줄 — FOR KEY SHARE</td>
 *       <td>주최자 줄을 잠그기 전에, 그 트랜잭션이 넣을 줄(이력 · 조작 기록)이 가리킬 사람을 id가 작은 쪽부터
 *           ({@link #lockForReferenceInIdOrder}). 조작하는 사람도 든다. 계정 줄 · 주최자 줄을 잡은 뒤 명단을 다시 읽어 권한을 다시
 *           확인한다({@link LockedOrganizer}). 관리자의 변경 승인은 요청한 사람의 계정 줄을 잡는다 — 넣을 줄이 가리키지는 않지만, 잡은 동안
 *           그 사람의 소속을 바꾸는 흐름(FOR UPDATE)이 기다려 읽은 명단(요청한 사람이 지금도 소유자인지)이 그대로다.</td>
 *       <td>공개 정보 편집, 로고 올리기 · 지우기, 주최자명 변경, 정산 계좌 · 사업자 정보 변경 요청, 정산 계좌 · 사업자 정보 변경 승인,
 *           행사 만들기, 위저드 단계 저장(1 · 5단계), 포스터 올리기, 구역 · 입구 추가 · 수정 · 삭제</td></tr>
 *   <tr><td>② 주최자 줄 — FOR UPDATE</td>
 *       <td>그 주최자 한 줄({@code OrganizerRepository#findByIdForUpdate}). 로고를 바꾸는 저장도 유일 칸(로고 열쇠)을 고쳐 같은 세기로
 *           잠근다.</td>
 *       <td>공개 정보 편집, 로고 올리기 · 지우기, 주최자명 변경, 정산 계좌 · 사업자 정보 변경 요청, 정산 계좌 · 사업자 정보 변경 승인(변경
 *           요청 줄보다 먼저)</td></tr>
 *   <tr><td>② 주최자 줄 — FOR KEY SHARE</td>
 *       <td>그 주최자 한 줄({@code OrganizerRepository#findByIdForKeyShare}). 그 주최자에 속한 행사를 만들거나 고치는 흐름이 행사 줄보다 먼저 —
 *           새 행사 줄과 조작 기록이 이 줄을 가리킨다. 위 FOR UPDATE와만 부딪혀, 공개 정보 편집 · 주최자명 변경 등이 끝날 때까지 기다린다. 같은
 *           주최자의 다른 행사를 고치는 흐름끼리는 막지 않는다. 계정 줄 · 주최자 줄을 잡은 뒤 명단을 다시 읽어 권한을 다시 확인한다(event
 *           서비스의 {@code EventEditingLocks}).</td>
 *       <td>행사 만들기, 위저드 단계 저장(1 · 5단계), 포스터 올리기, 구역 · 입구 추가 · 수정 · 삭제</td></tr>
 *   <tr><td>③ 행사 줄 — FOR UPDATE</td>
 *       <td>그 행사 한 줄({@code EventRepository#findByIdForUpdate}). 같은 행사의 단계 저장끼리 차례로 처리된다 — 뒤의 저장이 앞의 저장이 끝난 값을
 *           읽는다. 포스터를 바꾸는 저장도 유일 칸(포스터 열쇠)을 고쳐 같은 세기로 잠근다 — 뒤의 올리기는 앞의 포스터를 바꾸기 전 포스터로
 *           읽는다. 구역 · 입구 저장도 이 줄을 잡은 뒤 도면 · 입구 목록을 읽어, 같은 행사의 구역 · 입구 이름이 겹치는지를 차례로 본다.</td>
 *       <td>위저드 단계 저장(1 · 5단계), 포스터 올리기, 구역 · 입구 추가 · 수정 · 삭제</td></tr>
 *   <tr><td>④ 그 밖의 줄</td>
 *       <td>초대 → 소속, 신청서 · 소유자 이전 신청 · 변경 요청 · 이력 · 조작 기록 — FOR UPDATE, 조건부 갱신, 새 줄 넣기. 구역 · 무대 위치 ·
 *           입구 줄은 행사 줄을 쥔 채 잠그지 않고 고치고 넣는다(그 줄이 거는 외래 키 확인 잠금은 이미 쥔 행사 줄에 걸린다). 흐름마다의 차례는
 *           각 서비스에 적혀 있다.</td>
 *       <td>각 흐름</td></tr>
 * </table>
 *
 * <h2>외래 키 확인 잠금도 순서에 든다</h2>
 * 줄을 넣거나 외래 키 칸을 고치면 PostgreSQL이 그 줄이 가리키는 계정 줄 · 주최자 줄에 외래 키 확인 잠금(FOR KEY SHARE)을 건다. 이 잠금은
 * FOR UPDATE와만 부딪힌다.
 * <ul>
 *   <li><b>② 뒤에 넣을 줄이 가리킬 계정 줄은 ①에서 미리 잡는다.</b> 주최자명 변경 이력과 조작 기록은 조작하는 사람의 계정 줄을 가리킨다. 주최자
 *       줄을 FOR UPDATE로 쥔 채 그 외래 키 확인 잠금을 기다리면, 그 계정 줄을 먼저 FOR UPDATE로 잠그고(①) 소속 · 이력 · 조작 기록을 넣으며 주최자
 *       줄에 외래 키 확인 잠금을 기다리는 흐름(소유자 넘기기 · 내보내기 · 소유자 이전 신청 승인)과 맞물려 교착한다(#195).</li>
 *   <li><b>그 계정 줄을 FOR UPDATE가 아니라 FOR KEY SHARE로 잡는다.</b> 계정 줄을 잠그지 않는 흐름(운영자 초대, 앞으로의 모든 조작 기록)은 줄
 *       하나를 넣는 한 문장 안에서도 주최자 줄 → 계정 줄 차례로 외래 키 확인 잠금을 건다. ②의 흐름이 계정 줄을 FOR UPDATE로 잡으면 이들과 거꾸로
 *       맞물려 교착한다. FOR KEY SHARE는 외래 키 확인 잠금과 부딪히지 않아 이들을 막지 않고, ①의 FOR UPDATE와는 부딪혀 그 흐름이 끝날 때까지
 *       주최자 줄을 잡기 전에 기다린다.</li>
 *   <li><b>②(FOR UPDATE)를 쥔 흐름은 이미 있는 ④의 줄을 기다리지 않는다</b> — 그 주최자의 SNS 링크(주최자 줄을 쥐어야만 고친다)와 새 줄(이력 · 조작 기록 ·
 *       변경 요청)만 쓴다. 새 변경 요청은 유일 색인 때문에 같은 주최자 · 같은 종류의 요청 줄을 고치는 중인 트랜잭션을 기다릴 수 있는데, 그 줄을
 *       고치는 흐름(관리자의 승인 · 반려)은 주최자 줄을 기다리지 않거나(반려) 주최자 줄을 먼저 잡는다(승인). 그래서 ④를 먼저 바꾸고 주최자 줄에 닿는 흐름과도 맞물리지 않는다: 소유자 이전 신청 승인(신청을 바꾼 뒤 이력을 넣으며 외래 키 확인
 *       잠금), 주최자 신청 승인(신청서를 먼저 바꿔 두 관리자의 경합을 가른 뒤 주최자를 활동으로 — 심사 중인 주최자라 운영진이 없어 ②의 흐름과
 *       만나지도 않는다).</li>
 *   <li><b>행사 흐름은 주최자 줄 · 행사 줄을 쥔 뒤 새 줄을 넣거나 그 행사에 딸린 줄만 고친다</b> — 행사 줄(만들 때)과 조작 기록, 그 행사의
 *       구역 · 무대 위치 · 입구 줄. 그 줄들이 거는 외래 키 확인 잠금은 이미 ① · ② · ③에서 잡은 계정 줄 · 주최자 줄 · 행사 줄에 걸려 기다리지
 *       않고, 행사에 딸린 줄은 행사 줄을 쥔 흐름만 고쳐 그 줄에서 서로 기다리지 않는다. 주최자 줄을 FOR UPDATE로 쥐는 흐름(②)은 행사 줄을 기다리지 않으므로 행사 줄을
 *       쥔 흐름과 맞물리지 않는다. 앞으로 주최자 줄을 쥔 채 행사 줄을 잠그는 흐름(정지 2단계의 판매 멈추기, 주최자 닫기 등)도 이 차례(주최자 줄 →
 *       행사 줄)를 지킨다. 주최자 상태를 활동 밖으로 바꾸는 흐름(닫기 · 영구 정지)은 주최자 줄을 FOR UPDATE로 잠근다 — 그래야 행사 흐름의 외래 키
 *       확인 잠금과 부딪혀, 닫힌 주최자에 행사가 새로 생기거나 고쳐지지 않는다.</li>
 *   <li>④를 고친 뒤 계정 줄에 외래 키 확인 잠금이 걸리는 흐름(운영자 다시 초대 — 초대 줄을 고친 뒤 초대한 사람을 가리키는 조작 기록)은 그 계정
 *       줄을 FOR UPDATE로 쥔 채 같은 ④의 줄을 기다리는 흐름이 없어 그대로 둔다 — 초대 줄을 기다리는 수락 · 거절은 초대받은 사람의 계정 줄을
 *       잡는다. 그런 흐름이 생기면 그 계정 줄을 ①에서 먼저 잡는다.</li>
 * </ul>
 * 관련된 사람이 여럿이면 <b>id가 작은 쪽부터</b> 잠근다 — 같은 두 사람을 잠그는 두 흐름이 서로 반대 순서로 잠가 교착하지 않게.
 * <p>
 * 이 순서를 어긴 흐름이 있어 그래도 교착하면, 진 쪽 트랜잭션은 되돌려지고 API는 500이 아니라 409 CONCURRENT_REQUEST("다시 시도")로 답한다
 * (app-api ApiExceptionHandler — 예외 전체를 warn 로그로 남겨 어긴 흐름을 찾는다).
 */
public final class StaffAccountLocks {

    private StaffAccountLocks() {
    }

    /**
     * 그 사람들의 계정 줄을 id 순서로 FOR UPDATE로 잠그고 읽는다 — 잠금 순서 ①, 그 사람의 소속 · 계정을 바꾸는 흐름이 무엇이든 바꾸기 전에 맨 먼저
     * 부른다. 같은 id가 여러 번 오면 한 번만 잠근다.
     *
     * @return 잠근 계정 — 계정 id로 찾는다. 없는 계정은 담기지 않는다.
     */
    public static Map<Long, StaffAccount> lockInIdOrder(StaffAccountRepository repository, long... staffAccountIds) {
        return lockEachInIdOrder(repository::findByIdForUpdate, staffAccountIds);
    }

    /**
     * 그 사람들의 계정 줄에 id 순서로 외래 키 확인 잠금(FOR KEY SHARE)을 걸고 읽는다 — 잠금 순서 ①, 주최자 줄을 잠그는 흐름이 주최자 줄보다
     * 먼저 부른다. 그 트랜잭션이 넣을 줄(이력 · 조작 기록)이 가리킬 사람을 모두 넘긴다. 같은 id가 여러 번 오면 한 번만 잡는다.
     *
     * @return 잡은 계정 — 계정 id로 찾는다. 없는 계정은 담기지 않는다.
     */
    public static Map<Long, StaffAccount> lockForReferenceInIdOrder(StaffAccountRepository repository,
            long... staffAccountIds) {
        return lockEachInIdOrder(repository::findByIdForKeyShare, staffAccountIds);
    }

    private static Map<Long, StaffAccount> lockEachInIdOrder(LongFunction<Optional<StaffAccount>> lock,
            long... staffAccountIds) {
        Map<Long, StaffAccount> locked = new HashMap<>();
        LongStream.of(staffAccountIds).distinct().sorted().forEach(staffAccountId ->
                lock.apply(staffAccountId).ifPresent(found -> locked.put(staffAccountId, found)));
        return locked;
    }
}
