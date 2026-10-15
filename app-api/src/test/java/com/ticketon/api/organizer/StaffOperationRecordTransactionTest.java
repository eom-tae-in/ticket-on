package com.ticketon.api.organizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.StaffOperationRecordRepository;
import com.ticketon.application.organizer.service.RecordStaffOperationService;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationRecord;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 운영진 조작 기록은 조작과 같은 트랜잭션에서만 남긴다 — Spring 트랜잭션 프록시를 건 채로, 트랜잭션 밖에서 부르면 막히고 안에서 부르면
 * 남는지 본다. DB 없이 돈다(트랜잭션 관리자는 시작 여부만 기억하는 시험용). 관리자 조작 기록기와 같은 약속이다.
 */
@SpringJUnitConfig(StaffOperationRecordTransactionTest.TestConfiguration.class)
@DisplayName("운영진 조작 기록 — 조작과 같은 트랜잭션에서만")
class StaffOperationRecordTransactionTest {

    private static final RecordStaffOperationCommand PROFILE_EDITED = new RecordStaffOperationCommand(
            new StaffPermissionResult(12, 2, StaffRole.OPERATOR, StaffAction.EDIT_PUBLIC_PROFILE),
            StaffOperationTargetKind.ORGANIZER, 12, "주최자 소개 · SNS 링크 수정", Instant.parse("2026-10-15T05:30:00Z"));

    @Autowired
    private RecordStaffOperationUseCase recordStaffOperationUseCase;

    @Autowired
    private RecordingRepository recordRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("트랜잭션 밖에서 부르면 막고 아무것도 남기지 않는다 — 컨트롤러에서 조작 없이 기록만 남기는 길이 없다")
    void rejectsRecordingOutsideTransaction() {
        assertThatExceptionOfType(IllegalTransactionStateException.class)
                .isThrownBy(() -> recordStaffOperationUseCase.record(PROFILE_EDITED));

        assertThat(recordRepository.records).isEmpty();
    }

    @Test
    @DisplayName("조작의 트랜잭션 안에서 부르면 그 트랜잭션에 참여해 남긴다")
    void recordsInsideOperationTransaction() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> recordStaffOperationUseCase.record(PROFILE_EDITED));

        assertThat(recordRepository.records).singleElement()
                .satisfies(record -> assertThat(record.summary()).isEqualTo("주최자 소개 · SNS 링크 수정"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfiguration {

        @Bean
        RecordingRepository recordRepository() {
            return new RecordingRepository();
        }

        @Bean
        RecordStaffOperationService recordStaffOperationService(StaffOperationRecordRepository recordRepository) {
            return new RecordStaffOperationService(recordRepository);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new StartedFlagTransactionManager();
        }
    }

    static final class RecordingRepository implements StaffOperationRecordRepository {

        private final List<StaffOperationRecord> records = new ArrayList<>();

        @Override
        public void add(StaffOperationRecord record) {
            records.add(record);
        }
    }

    /** 자원 없이 "지금 트랜잭션이 열려 있는지"만 기억하는 시험용 트랜잭션 관리자. */
    static final class StartedFlagTransactionManager extends AbstractPlatformTransactionManager {

        private final ThreadLocal<Boolean> started = ThreadLocal.withInitial(() -> false);

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return started.get();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            started.set(true);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            started.set(false);
        }
    }
}
