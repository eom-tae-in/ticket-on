package com.ticketon.domain.policy;

/**
 * 파일 용량 — 올리는 파일의 상한(로고 · 포스터 등)에 쓴다.
 * <p>
 * 1KB는 1,024바이트, 1MB는 1,048,576바이트로 센다. 운영체제가 보여 주는 용량(1MB = 1,000,000바이트로 세기도 한다)보다 같거나 넉넉하게
 * 받아, 화면에서 "2MB"로 보이는 파일이 거절되는 일이 없게 한다.
 *
 * @param bytes 바이트 수 (1 이상)
 */
public record FileSize(long bytes) {

    private static final long BYTES_PER_KILOBYTE = 1024;
    private static final long BYTES_PER_MEGABYTE = BYTES_PER_KILOBYTE * 1024;

    public FileSize {
        if (bytes < 1) {
            throw new IllegalArgumentException("파일 용량은 1바이트 이상이어야 합니다: " + bytes);
        }
    }

    /** 그 메가바이트 수의 용량. 예: {@code ofMegabytes(2)} → 2,097,152바이트. */
    public static FileSize ofMegabytes(long megabytes) {
        return new FileSize(Math.multiplyExact(megabytes, BYTES_PER_MEGABYTE));
    }

    /** 그 바이트 수가 이 용량을 넘는지 — 상한으로 쓸 때, 상한과 같은 크기는 넘지 않은 것이다. */
    public boolean isExceededBy(long sizeInBytes) {
        return sizeInBytes > bytes;
    }

    /** 사람이 읽는 꼴 — 메가바이트로 나누어떨어지면 "2MB", 킬로바이트로 나누어떨어지면 "512KB", 아니면 "100B". */
    @Override
    public String toString() {
        if (bytes % BYTES_PER_MEGABYTE == 0) {
            return bytes / BYTES_PER_MEGABYTE + "MB";
        }
        if (bytes % BYTES_PER_KILOBYTE == 0) {
            return bytes / BYTES_PER_KILOBYTE + "KB";
        }
        return bytes + "B";
    }
}
