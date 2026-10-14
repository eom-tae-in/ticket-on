package com.ticketon.domain.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("도메인 공통 예외와 오류 코드")
class DomainExceptionTest {

    /** 문서가 정해 둔 문구로 만든 시험용 오류 코드. */
    private enum SampleErrorCode implements ErrorCode {
        SEAT_ALREADY_TAKEN("방금 다른 분이 선택했습니다"),
        COUPON_NOT_APPLICABLE("이 행사에서 사용할 수 없는 코드입니다");

        private final String message;

        SampleErrorCode(String message) {
            this.message = message;
        }

        @Override
        public String message() {
            return message;
        }
    }

    @Test
    @DisplayName("enum이 구현한 오류 코드는 상수 이름이 코드다")
    void enumConstantNameIsCode() {
        assertThat(SampleErrorCode.SEAT_ALREADY_TAKEN.code()).isEqualTo("SEAT_ALREADY_TAKEN");
        assertThat(SampleErrorCode.SEAT_ALREADY_TAKEN.message()).isEqualTo("방금 다른 분이 선택했습니다");
    }

    @Test
    @DisplayName("동시성 패배는 충돌 예외에 오류 코드를 담는다 (docs/06 \"두 사람이 동시에 같은 자리를 눌렀다면\")")
    void conflictCarriesErrorCode() {
        DomainException exception = new ConflictException(SampleErrorCode.SEAT_ALREADY_TAKEN);

        assertThat(exception.errorCode()).isEqualTo(SampleErrorCode.SEAT_ALREADY_TAKEN);
        assertThat(exception.errorCode().message()).isEqualTo("방금 다른 분이 선택했습니다");
    }

    @Test
    @DisplayName("로그용 메시지는 코드와 detail이고, 화면 문구는 담지 않는다")
    void logMessageHasCodeAndDetailOnly() {
        DomainException exception = new RuleViolationException(SampleErrorCode.COUPON_NOT_APPLICABLE, "couponCode=SPRING10");

        assertThat(exception.getMessage())
                .isEqualTo("COUPON_NOT_APPLICABLE — couponCode=SPRING10")
                .doesNotContain(SampleErrorCode.COUPON_NOT_APPLICABLE.message());
    }

    @Test
    @DisplayName("detail이 없으면 로그용 메시지는 코드뿐이다")
    void logMessageWithoutDetailIsCode() {
        DomainException exception = new NotFoundException(SampleErrorCode.SEAT_ALREADY_TAKEN);

        assertThat(exception.getMessage()).isEqualTo("SEAT_ALREADY_TAKEN");
    }

    @Test
    @DisplayName("네 종류 모두 도메인 예외이자 RuntimeException이다")
    void allKindsAreDomainExceptions() {
        assertThat(new RuleViolationException(SampleErrorCode.COUPON_NOT_APPLICABLE)).isInstanceOf(DomainException.class);
        assertThat(new NotFoundException(SampleErrorCode.COUPON_NOT_APPLICABLE)).isInstanceOf(DomainException.class);
        assertThat(new PermissionDeniedException(SampleErrorCode.COUPON_NOT_APPLICABLE)).isInstanceOf(DomainException.class);
        assertThat(new ConflictException(SampleErrorCode.SEAT_ALREADY_TAKEN)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("오류 코드 없이는 만들 수 없다")
    void errorCodeIsRequired() {
        assertThatThrownBy(() -> new RuleViolationException(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("errorCode");
    }
}
