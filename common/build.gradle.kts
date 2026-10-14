// 순수 유틸만 (시각·영업일 계산 등). 설정·프레임워크 의존을 넣지 않는다.
plugins {
    id("ticketon.java-conventions")
    // 테스트 픽스처(고정 생성기 등)를 다른 모듈의 테스트에서 쓰게 한다: testImplementation(testFixtures(project(":common")))
    `java-test-fixtures`
}
