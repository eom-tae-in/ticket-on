"""Gradle 테스트 결과(JUnit XML)를 모아 요약하고, 건너뛴 테스트가 있으면 실패한다.

CI에는 Docker가 있으므로 Testcontainers 테스트까지 전부 돌아야 한다.
건너뛰었다면 CI 환경에 문제가 있다는 뜻이므로 녹색으로 넘기지 않는다.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ElementTree


def collect_results():
    totals = {"tests": 0, "skipped": 0, "failures": 0, "errors": 0}
    skipped_tests = []
    for path in sorted(glob.glob("**/build/test-results/test/*.xml", recursive=True)):
        suite = ElementTree.parse(path).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, 0))
        for case in suite.iter("testcase"):
            if case.find("skipped") is not None:
                skipped_tests.append(f"{case.get('classname')}.{case.get('name')}")
    return totals, skipped_tests


def write_job_summary(totals, skipped_tests):
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary_path:
        return
    with open(summary_path, "a", encoding="utf-8") as summary:
        summary.write("### 서버 테스트 결과\n\n")
        summary.write("| 전체 | 건너뜀 | 실패 | 오류 |\n|---|---|---|---|\n")
        summary.write(f"| {totals['tests']} | {totals['skipped']} | {totals['failures']} | {totals['errors']} |\n")
        if skipped_tests:
            summary.write("\n건너뛴 테스트:\n\n")
            summary.writelines(f"- `{name}`\n" for name in skipped_tests)


def main():
    totals, skipped_tests = collect_results()
    print(f"테스트 {totals['tests']}개 · 건너뜀 {totals['skipped']} · 실패 {totals['failures']} · 오류 {totals['errors']}")
    for name in skipped_tests:
        print(f"  건너뜀: {name}")
    write_job_summary(totals, skipped_tests)
    if totals["tests"] == 0:
        print("테스트 결과를 찾지 못했다.")
        return 1
    if skipped_tests:
        print("CI에서는 모든 테스트가 돌아야 한다. 건너뛴 테스트가 있어 실패로 처리한다.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
