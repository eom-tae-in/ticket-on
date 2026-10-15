package com.ticketon.application.admin.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;

/**
 * 관리자 계정 발급 — 들어오는 포트. 최고 관리자만 한다(docs/01 "계정 4종", "관리자 2등급", docs/13 "관리자 계정 발급 ※ 최고 관리자만").
 * <p>
 * 이메일·비밀번호·등급으로 새 관리자 계정을 만들고, 관리자 조작 기록에 한 줄 남긴다 — 둘은 함께 저장되거나 함께 되돌려진다.
 */
public interface IssueAdminAccountUseCase {

    /**
     * @throws PermissionDeniedException 발급하는 사람이 최고 관리자가 아니면(SUPER_ADMIN_ONLY_ACTION)
     * @throws RuleViolationException    같은 이메일의 관리자 계정이 있으면(ADMIN_EMAIL_ALREADY_REGISTERED)
     * @throws ConflictException         같은 이메일로 동시에 발급해 다른 요청이 먼저 저장했으면(ADMIN_EMAIL_ALREADY_REGISTERED)
     */
    IssueAdminAccountResult issue(IssueAdminAccountCommand command);
}
