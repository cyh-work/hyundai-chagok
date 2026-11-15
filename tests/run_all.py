"""현재 제품 소스의 공개 계약 검증을 실행하고 실제 결과를 한 파일로 모은다."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
import os
from pathlib import Path
import subprocess
import sys
import time


@dataclass(frozen=True)
class SuiteSpecification:
    """각 독립 검증 묶음의 이름과 실행 파일을 지정한다."""

    name: str
    script: str


class ProductTestRunner:
    """공통 실행 환경을 주입하고 실패한 묶음을 성공으로 합산하지 않는다."""

    SUITES = (
        SuiteSpecification("records", "records/run.py"),
        SuiteSpecification("interaction", "interaction/run_suite.py"),
        SuiteSpecification("integrations", "integrations/run_suite.py"),
    )

    def __init__(self, root: Path, output: Path) -> None:
        """프로젝트 위치와 결과 저장 위치를 분리해 환경별 실행을 허용한다."""
        self.root = root
        self.output = output
        self.environment = self._environment()

    def _environment(self) -> dict[str, str]:
        """환경변수를 우선하며 이 프로젝트의 기존 개발 도구를 대안으로 사용한다."""
        environment = dict(os.environ)
        local = self.root / ".local-dev"
        defaults = {
            "JAVA_HOME": local / "jdk-17",
            "ANDROID_HOME": local / "android-sdk",
            "GRADLE_USER_HOME": local / "gradle-home",
            "JSON_JAR": local / "test-dependencies/json-20240303.jar",
        }
        for name, candidate in defaults.items():
            if not environment.get(name) and candidate.exists():
                environment[name] = str(candidate)
        environment["PYTHONUTF8"] = "1"
        return environment

    def _run_suite(self, specification: SuiteSpecification) -> dict[str, object]:
        """묶음별 종료 코드·원본 출력·구조화 결과를 보존한다."""
        destination = self.output / specification.name
        destination.mkdir(parents=True, exist_ok=True)
        script = self.root / "tests" / specification.script
        started = time.monotonic()
        if not script.is_file():
            return {
                "name": specification.name,
                "status": "FAIL",
                "error": "검증 실행 파일이 없습니다.",
                "assertions": 0,
            }

        # 대화형 창이나 에뮬레이터 변경 없이 자동 검증만 실행한다.
        command = [sys.executable, "-X", "utf8", str(script), "--output", str(destination)]
        try:
            completed = subprocess.run(
                command,
                cwd=self.root,
                env=self.environment,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=600,
                check=False,
            )
            (destination / "runner.log").write_text(
                completed.stdout + completed.stderr, encoding="utf-8"
            )
            exit_code = completed.returncode
        except subprocess.TimeoutExpired:
            return {
                "name": specification.name,
                "status": "FAIL",
                "error": "자동 검증이 600초 안에 끝나지 않았습니다.",
                "assertions": 0,
            }

        # 이전 결과를 읽지 않도록 각 실행은 새로운 출력 디렉터리를 사용한다.
        result_file = destination / "result.json"
        if not result_file.is_file():
            return {
                "name": specification.name,
                "status": "FAIL",
                "exitCode": exit_code,
                "error": "구조화된 검증 결과가 생성되지 않았습니다.",
                "assertions": 0,
            }
        try:
            details = json.loads(result_file.read_text(encoding="utf-8-sig"))
        except (json.JSONDecodeError, OSError) as failure:
            return {
                "name": specification.name,
                "status": "FAIL",
                "exitCode": exit_code,
                "error": type(failure).__name__,
                "assertions": 0,
            }

        passed = exit_code == 0 and details.get("status") == "PASS"
        return {
            "name": specification.name,
            "status": "PASS" if passed else "FAIL",
            "exitCode": exit_code,
            "elapsedSeconds": round(time.monotonic() - started, 2),
            "assertions": details.get("assertions", 0),
            "result": str(result_file.relative_to(self.output)),
            "details": details,
        }

    def run(self, requested: list[str]) -> int:
        """모든 선택 묶음의 결과를 보고하고 하나라도 실패하면 실패 종료한다."""
        self.output.mkdir(parents=True, exist_ok=True)
        results = []
        for specification in self.SUITES:
            if requested and specification.name not in requested:
                continue
            result = self._run_suite(specification)
            results.append(result)
            print(f"{specification.name}: {result['status']}", flush=True)

        passed = bool(results) and all(result["status"] == "PASS" for result in results)
        summary = {
            "status": "PASS" if passed else "FAIL",
            "assertions": sum(int(result.get("assertions", 0)) for result in results),
            "suites": results,
            "scope": "제품 공개 계약 자동 검증; 실제 기기와 외부 서비스 성공을 대신하지 않음",
        }
        (self.output / "result.json").write_text(
            json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        print(f"Overall: {summary['status']} / {summary['assertions']} assertions")
        print(f"Result: {self.output / 'result.json'}")
        return 0 if passed else 1


def main() -> int:
    """선택 실행과 출력 위치를 해석하며 기존 결과 폴더의 재사용을 막는다."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--suite", action="append", choices=["records", "interaction", "integrations"])
    arguments = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = arguments.output
    if output is None:
        output = root / "build/test-results" / time.strftime("run-%Y%m%d-%H%M%S")
    output = output.resolve()
    if output.exists() and any(output.iterdir()):
        parser.error("기존 결과와 섞이지 않도록 비어 있는 새 출력 위치를 지정하세요.")
    return ProductTestRunner(root, output).run(arguments.suite or [])


if __name__ == "__main__":
    sys.exit(main())
