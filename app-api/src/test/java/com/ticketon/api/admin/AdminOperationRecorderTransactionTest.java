package com.ticketon.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.admin.port.out.AdminOperationRecordRepository;
import com.ticketon.application.admin.service.AdminOperationRecorder;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.admin.AdminOperationRecord;
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
 * 관리자 조작 기록기는 조작과 같은 트랜잭션에서만 남긴다 — Spring 트랜잭션 프록시를 건 채로, 트랜잭션 밖에서 부르면 막히고 안에서 부르면
 * 남는지 본다. DB 없이 돈다(트랜잭션 관리자는 시작 여부만 기억하는 시험용).
 */
@SpringJUnitConfig(AdminOperationRecorderTransactionTest.TestConfiguration.class)
@DisplayName("관리자 조작 기록기 — 조작과 같은 트랜잭션에서만")
class AdminOperationRecorderTransactionTest {

    private static final AdminOperationRecord ISSUED = AdminOperationRecord.adminAccountIssued(
            AccountRef.of(AccountKind.ADMIN, 1), 2, EmailAddress.of("new-admin@ticket-on.example"), AdminGrade.ADMIN,
            Instant.parse("2026-10-15T05:30:00Z"));

    @Autowired
    private AdminOperationRecorder recorder;

    @Autowired
    private RecordingRepository recordRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("트랜잭션 밖에서 부르면 막고 아무것도 남기지 않는다 — 조작 없이 기록만 남거나, 기록이 조작과 따로 저장되지 않게")
    void rejectsRecordingOutsideTransaction() {
        assertThatExceptionOfType(IllegalTransactionStateException.class).isThrownBy(() -> recorder.record(ISSUED));

        assertThat(recordRepository.records).isEmpty();
    }

    @Test
    @DisplayName("조작의 트랜잭션 안에서 부르면 그 트랜잭션에 참여해 남긴다")
    void recordsInsideOperationTransaction() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> recorder.record(ISSUED));

        assertThat(recordRepository.records).containsExactly(ISSUED);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfiguration {

        @Bean
        RecordingRepository recordRepository() {
            return new RecordingRepository();
        }

        @Bean
        AdminOperationRecorder adminOperationRecorder(AdminOperationRecordRepository recordRepository) {
            return new AdminOperationRecorder(recordRepository);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new StartedFlagTransactionManager();
        }
    }

    static final class RecordingRepository implements AdminOperationRecordRepository {

        private final List<AdminOperationRecord> records = new ArrayList<>();

        @Override
        public void add(AdminOperationRecord record) {
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
