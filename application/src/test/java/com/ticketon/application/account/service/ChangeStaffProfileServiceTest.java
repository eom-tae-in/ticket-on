package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.account.port.in.ChangeStaffProfileCommand;
import com.ticketon.application.account.port.in.MyStaffProfileResult;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.NicknameAvailability;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffIntroduction;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.RuleViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("내 프로필 편집 유스케이스 — 닉네임 · 한 줄 소개")
class ChangeStaffProfileServiceTest {

    private static final String INTRODUCTION = "극단에서 연출하고 있습니다";

    private final FakeStaffAccountRepository accountRepository = new FakeStaffAccountRepository();
    private final StoredStaffAccounts accounts = new StoredStaffAccounts(accountRepository);

    private final ChangeStaffProfileService service = new ChangeStaffProfileService(accountRepository);

    @Test
    @DisplayName("닉네임과 소개를 함께 바꿔 저장하고, 앞뒤 공백을 걷은 값과 첫 글자 표시를 돌려준다 — 이름 · 이메일은 돌려주지 않는다")
    void changesAndSavesProfile() {
        long id = accounts.active("junghoon@example.com", "조용한고래");

        MyStaffProfileResult result = service.changeProfile(command(id, " 푸른바람 ", " " + INTRODUCTION + " "));

        assertThat(result).isEqualTo(new MyStaffProfileResult(id, "푸른바람", INTRODUCTION, "푸", (int) (id % 8)));
        StaffAccount saved = accountRepository.findById(id).orElseThrow();
        assertThat(saved.nickname()).isEqualTo(Nickname.of("푸른바람"));
        assertThat(saved.introduction()).contains(new StaffIntroduction(INTRODUCTION));
    }

    @Test
    @DisplayName("닉네임은 그대로 두고 소개만 적어도 저장한다")
    void savesIntroductionOnly() {
        long id = accounts.active("junghoon@example.com", "조용한고래");

        MyStaffProfileResult result = service.changeProfile(command(id, "조용한고래", INTRODUCTION));

        assertThat(result.introduction()).isEqualTo(INTRODUCTION);
        assertThat(accountRepository.findById(id).orElseThrow().introduction())
                .contains(new StaffIntroduction(INTRODUCTION));
    }

    @Test
    @DisplayName("소개를 비우면 지운다 — 소개는 선택이다")
    void removesIntroduction() {
        long id = accounts.active("junghoon@example.com", "조용한고래");
        service.changeProfile(command(id, "조용한고래", INTRODUCTION));

        MyStaffProfileResult result = service.changeProfile(command(id, "조용한고래", null));

        assertThat(result.introduction()).isNull();
        assertThat(accountRepository.findById(id).orElseThrow().introduction()).isEmpty();
    }

    @Test
    @DisplayName("닉네임을 바꾸면 옛 닉네임이 풀려 다른 운영진이 쓸 수 있다 — 이메일·이름 등 다른 정보는 그대로다")
    void releasesOldNickname() {
        long id = accounts.active("junghoon@example.com", "조용한고래");

        service.changeProfile(command(id, "푸른바람", null));

        assertThat(accountRepository.nicknameAvailability(Nickname.of("조용한고래"))).isEqualTo(NicknameAvailability.AVAILABLE);
        assertThat(accountRepository.findById(id).orElseThrow().personalInformation().email().value())
                .isEqualTo("junghoon@example.com");
    }

    @Test
    @DisplayName("다른 운영진이 쓰는 닉네임으로는 바꿀 수 없다 — 함께 적은 소개도 바꾸지 않는다")
    void rejectsTakenNickname() {
        long id = accounts.active("junghoon@example.com", "조용한고래");
        accounts.active("dohyun@example.com", "푸른바람");

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> service.changeProfile(command(id, "푸른바람", INTRODUCTION)))
                .extracting(DomainException::errorCode)
                .isEqualTo(AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN);
        StaffAccount unchanged = accountRepository.findById(id).orElseThrow();
        assertThat(unchanged.nickname()).isEqualTo(Nickname.of("조용한고래"));
        assertThat(unchanged.introduction()).isEmpty();
    }

    @Test
    @DisplayName("닉네임과 소개가 지금과 같으면 저장하지 않고 그대로 돌려준다")
    void unchangedProfileIsNotSaved() {
        long id = accounts.active("junghoon@example.com", "조용한고래");
        accountRepository.letAnotherNicknameChangeWinNextSave();

        MyStaffProfileResult result = service.changeProfile(command(id, "조용한고래", null));

        assertThat(result.nickname()).isEqualTo("조용한고래");
        assertThat(result.introduction()).isNull();
    }

    @Test
    @DisplayName("확인은 통과했지만 저장 직전 다른 운영진이 같은 닉네임을 먼저 저장하면 충돌이 그대로 나간다")
    void propagatesNicknameRaceConflict() {
        long id = accounts.active("junghoon@example.com", "조용한고래");
        accountRepository.letAnotherNicknameChangeWinNextSave();

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.changeProfile(command(id, "푸른바람", INTRODUCTION)))
                .extracting(DomainException::errorCode)
                .isEqualTo(AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN);
    }

    @Test
    @DisplayName("인증 대기 계정은 바꿀 수 없다")
    void rejectsPendingAccount() {
        long id = accounts.pending("junghoon@example.com", "조용한고래");

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> service.changeProfile(command(id, "푸른바람", INTRODUCTION)))
                .extracting(DomainException::errorCode)
                .isEqualTo(AccountErrorCode.STAFF_NOT_ACTIVE);
    }

    private static ChangeStaffProfileCommand command(long staffAccountId, String nickname, String introduction) {
        return new ChangeStaffProfileCommand(AccountRef.of(AccountKind.STAFF, staffAccountId), Nickname.of(nickname),
                StaffIntroduction.fromInput(introduction).orElse(null));
    }
}
