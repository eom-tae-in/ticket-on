package com.ticketon.domain.organizer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("주최자 심사 체크리스트 (docs/01 \"심사 화면\")")
class OrganizerReviewChecklistItemTest {

    @Test
    @DisplayName("문서의 세 항목이 문서 글 그대로, 문서 순서대로 있다")
    void threeItemsInDocumentOrder() {
        assertThat(Arrays.stream(OrganizerReviewChecklistItem.values()).map(OrganizerReviewChecklistItem::label))
                .containsExactly("연락처로 통화 확인", "링크·이력으로 실재 확인", "행사 계획이 구체적임");
    }
}
