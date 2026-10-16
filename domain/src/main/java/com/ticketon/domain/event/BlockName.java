package com.ticketon.domain.event;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import java.util.Objects;
import java.util.Optional;

/**
 * 블록 이름 — "1층", "2층", "좌측", "중앙"(docs/02 "블록"). 블록은 좌석 번호를 유일하게 만드는 단위라, 블록이 둘 이상이면 이름이 좌석 표기 앞머리가
 * 된다("1층 12열 7번", {@link SeatLabel}).
 * <p>
 * <b>이름은 자유 입력이다.</b> 처음에는 A~Z 고정 식별자에 표시 이름을 따로 받는 안이었으나 주최자가 두 군데를 입력하게 되어 이름 하나로 통합했다
 * (docs/99 D "좌석 식별"). 대신 같은 이름의 블록 둘, 구역 이름과 겹치는 블록 이름은 받지 않는다 — 겹치는지는 도면이 본다({@code FloorPlan}).
 * 비워 두면 "블록 1", "블록 2"를 붙인다({@link #automatic}).
 * <p>
 * 적은 값에서 앞뒤 공백을 걷고 유니코드 NFC(완성형)로 맞춘다. 한글은 화면에 똑같이 보이는 글자를 자모를 늘어놓은 꼴(NFD)로 적을 수 있어, 맞추지
 * 않으면 같은 "1층" 둘을 가려내지 못한다. 맞춘 값이 {@value ProposedPolicy#BLOCK_NAME_MAXIMUM_LENGTH}자를 넘으면 받지 않는다
 * ({@link ProposedPolicy#BLOCK_NAME_MAXIMUM_LENGTH}, 확인 필요). 글자 수는 사람이 세는 대로 센다(한글 한 자, 이모지 하나가 한 자).
 * <p>
 * 판매 중에도 고칠 수 있고 이미 팔린 표의 표기도 따라 바뀐다 — 이름은 블록 한 곳에만 있고 티켓은 좌석을 가리키기만 한다. 변경 이력은 남기지
 * 않는다(docs/02 "판매 중에도 블록 이름을 고칠 수 있습니다").
 *
 * @param value 앞뒤 공백을 걷고 NFC로 맞춘 이름
 */
public record BlockName(String value) {

    /** 비워 둔 블록에 붙이는 이름의 앞머리 — "블록 1"의 "블록"(docs/02 "블록"). */
    static final String AUTOMATIC_NAME_PREFIX = "블록";

    public BlockName {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("블록 이름이 비어 있습니다 — 비운 이름은 '블록 N'을 붙입니다");
        }
        if (exceedsMaximumLength(value)) {
            throw new IllegalArgumentException("블록 이름은 " + ProposedPolicy.BLOCK_NAME_MAXIMUM_LENGTH + "자 이하여야 합니다");
        }
    }

    /**
     * 적은 값으로 만든다. 앞뒤 공백을 걷고 NFC로 맞춘다.
     *
     * @throws IllegalArgumentException 공백을 걷으면 비거나, 맞춘 값이 최대 길이를 넘으면
     */
    public static BlockName of(String input) {
        return new BlockName(input);
    }

    /**
     * 주최자가 적은 값으로 만든다. 적지 않았거나 공백뿐이면 비어 있다 — 도면이 "블록 N"을 붙인다({@code FloorPlan#addBlock}).
     *
     * @param input 적은 값. 적지 않았으면 null
     * @throws IllegalArgumentException 맞춘 값이 최대 길이를 넘으면
     */
    public static Optional<BlockName> fromInput(String input) {
        if (input == null || normalize(input).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BlockName(input));
    }

    /** {@link #fromInput}으로 받을 수 있는 값인지 — 적지 않았거나 공백뿐인 값도 받는다(자동 이름). 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isAcceptable(String input) {
        return input == null || !exceedsMaximumLength(normalize(input));
    }

    /**
     * 비워 둔 블록에 붙이는 이름 — "블록 1", "블록 2"(docs/02 "블록"). 몇 번을 붙일지는 도면이 고른다({@code FloorPlan#addBlock}).
     *
     * @param sequence 1 이상
     */
    static BlockName automatic(int sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("자동 이름의 번호는 1 이상이어야 합니다: " + sequence);
        }
        return new BlockName(AUTOMATIC_NAME_PREFIX + " " + sequence);
    }

    private static String normalize(String input) {
        return Normalizer.normalize(input.strip(), Normalizer.Form.NFC);
    }

    private static boolean exceedsMaximumLength(String normalized) {
        return normalized.codePointCount(0, normalized.length()) > ProposedPolicy.BLOCK_NAME_MAXIMUM_LENGTH;
    }
}
