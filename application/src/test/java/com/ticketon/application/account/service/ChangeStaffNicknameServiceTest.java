package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.account.port.in.ChangeStaffNicknameCommand;
import com.ticketon.application.account.port.in.ChangeStaffNicknameResult;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.NicknameAvailability;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.RuleViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("운영진 닉네임 변경 유스케이스")
class ChangeStaffNicknameServiceTest {

    private final FakeStaffAccountRepository accountRepository = new FakeStaffAccountRepository();
    private final StoredStaffAccounts accounts = new StoredStaffAccounts(accountRepository);

    private final ChangeStaffNicknameService service = new ChangeStaffNicknameService(accountRepository);

    @Test
    @DisplayName("아무도 쓰지 않는 닉네임으로 바꿔 저장하고, 앞뒤 공백을 걷은 새 닉네임을 돌려준다")
    void changesAndSavesNickname() {
        long id = accounts.active("junghoon@example.com", "조용한고래");

        ChangeStaffNicknameResult result = service.changeNickname(command(id, " 푸른바람 "));

        assertThat(result.nickname()).isEqualTo("푸른바람");
        assertThat(accountRepository.findById(id).orElseThrow().nickname()).isEqualTo(Nickname.of("푸른바람"));
    }

    @Test
    @DisplayName("바꾸면 옛 닉네임이 풀려 다른 운영진이 쓸 수 있다 — 이메일·이름 등 다른 정보는 그대로다")
    void releasesOldNickname() {
        long id = accounts.active("junghoon@example.com", "조용한고래");

        service.changeNickname(command(id, "푸른바람"));

        assertThat(accountRepository.nicknameAvailability(Nickname.of("조용한고래"))).isEqualTo(NicknameAvailability.AVAILABLE);
        assertThat(accountRepository.findById(id).orElseThrow().personalInformation().email().value())
                .isEqualTo("junghoon@example.com");
    }

    @Test
    @DisplayName("다른 운영진이 쓰는 닉네임으로는 바꿀 수 없다 — 닉네임은 그대로다")
    void rejectsTakenNickname() {
        long id = accounts.active("junghoon@example.com", "조용한고래");
        accounts.active("dohyun@example.com", "푸른바람");

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> service.changeNickname(command(id, "푸른바람")))
                .extracting(DomainException::errorCode)
                .isEqualTo(AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN);
        assertThat(accountRepository.findById(id).orElseThrow().nickname()).isEqualTo(Nickname.of("조용한고래"));
    }

    @Test
    @DisplayName("지금 닉네임과 같으면 저장하지 않고 그대로 돌려준다")
    void sameNicknameIsNotSaved() {
        long id = accounts.active("junghoon@example.com", "조용한고래");
        accountRepository.letAnotherNicknameChangeWinNextSave();

        ChangeStaffNicknameResult result = service.changeNickname(command(id, "조용한고래"));

        assertThat(result.nickname()).isEqualTo("조용한고래");
    }

    @Test
    @DisplayName("확인은 통과했지만 저장 직전 다른 운영진이 같은 닉네임을 먼저 저장하면 충돌이 그대로 나간다")
    void propagatesNicknameRaceConflict() {
        long id = accounts.active("junghoon@example.com", "조용한고래");
        accountRepository.letAnotherNicknameChangeWinNextSave();

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.changeNickname(command(id, "푸른바람")))
                .extracting(DomainException::errorCode)
                .isEqualTo(AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN);
    }

    @Test
    @DisplayName("인증 대기 계정은 바꿀 수 없다")
    void rejectsPendingAccount() {
        long id = accounts.pending("junghoon@example.com", "조용한고래");

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> service.changeNickname(command(id, "푸른바람")))
                .extracting(DomainException::errorCode)
                .isEqualTo(AccountErrorCode.STAFF_NOT_ACTIVE);
    }

    private static ChangeStaffNicknameCommand command(long staffAccountId, String nickname) {
        return new ChangeStaffNicknameCommand(AccountRef.of(AccountKind.STAFF, staffAccountId), Nickname.of(nickname));
    }
}
