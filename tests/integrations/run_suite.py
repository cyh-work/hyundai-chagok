"""현재 제품의 외부 연동 계약을 네트워크·기기 접근 없이 검사한다."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile


class IntegrationSuite:
    """제품을 실제 SDK로 컴파일하고 시스템 경계 대역과 함께 실행한다."""

    def __init__(self, output: Path) -> None:
        """프로젝트와 환경 설정에서 실행 도구·결과 위치를 확정한다."""
        self.source = Path(__file__).resolve().parent
        self.root = self.source.parents[1]
        self.output = output.resolve()
        self.output.mkdir(parents=True, exist_ok=True)
        self.java_home = Path(os.environ.get("JAVA_HOME", self.root / ".local-dev/jdk-17"))
        self.sdk_home = Path(os.environ.get("ANDROID_HOME", self.root / ".local-dev/android-sdk"))
        self.gradle_home = Path(os.environ.get("GRADLE_USER_HOME", self.root / ".local-dev/gradle-home"))
        self.cache = self.gradle_home / "caches/modules-2/files-2.1"
        suffix = ".exe" if os.name == "nt" else ""
        self.javac = self.java_home / "bin" / ("javac" + suffix)
        self.java = self.java_home / "bin" / ("java" + suffix)
        self.android = self.sdk_home / "platforms/android-35/android.jar"
        self.results: list[dict[str, object]] = []
        self.source_hashes: dict[str, str] = {}

    def execute(self, arguments: list[str | Path], log_name: str) -> str:
        """프로세스 출력과 종료 코드를 남기고 실패 시 해당 단계를 중단한다."""
        result = subprocess.run(
            [str(argument) for argument in arguments], cwd=self.root,
            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120,
        )
        text = result.stdout + result.stderr
        (self.output / log_name).write_text(text, encoding="utf-8")
        if result.returncode != 0:
            raise RuntimeError(f"{log_name}: exit={result.returncode}\n{text}")
        return text

    def artifact(self, group: str, module: str, version: str, extension: str) -> Path:
        """이미 빌드에 사용한 정확한 버전의 캐시 파일만 사용한다."""
        folder = self.cache / group / module / version
        files = sorted(folder.glob(f"*/{module}-{version}.{extension}"))
        if not files:
            raise FileNotFoundError(f"Missing cached artifact: {group}:{module}:{version}")
        return files[0]

    def sdk_jar(self, module: str, version: str) -> Path:
        """기존 SDK AAR의 바이트코드를 결과 폴더에 추출한다."""
        archive = self.artifact("ai.pleos.playground", module, version, "aar")
        target = self.output / f"{module}-{version}.jar"
        with zipfile.ZipFile(archive) as contents:
            target.write_bytes(contents.read("classes.jar"))
        return target

    def compile(self, name: str, sources: list[Path], classpath: list[Path]) -> Path:
        """현재 소스를 컴파일하고 사용한 제품 소스 해시를 기록한다."""
        classes = self.output / (name + "-classes")
        classes.mkdir(exist_ok=True)
        for source in sources:
            if source.is_relative_to(self.root / "app/src"):
                self.source_hashes[str(source.relative_to(self.root)).replace("\\", "/")] = (
                    hashlib.sha256(source.read_bytes()).hexdigest()
                )
        self.execute([
            self.javac, "-encoding", "UTF-8", "-cp", os.pathsep.join(map(str, classpath)),
            "-d", classes, *sources,
        ], name + "-compile.txt")
        return classes

    def run(self, name: str, entry: str, classpath: list[Path], explicit_checks: int = 0) -> None:
        """공개 계약 검증을 실행하고 실제 성공 출력에서 단언 수를 읽는다."""
        text = self.execute([
            self.java, "-Dfile.encoding=UTF-8", "-cp", os.pathsep.join(map(str, classpath)), entry,
        ], name + "-result.txt")
        match = re.search(r"assertions[=: ]+(\d+)", text)
        if match is None:
            match = re.search(r"PASS (\d+) lifecycle checks", text)
        assertions = int(match.group(1)) if match else explicit_checks
        scenarios = re.search(r"scenarios[=: ]+(\d+)", text)
        self.results.append({
            "name": name, "status": "PASS", "assertions": assertions,
            "scenarios": int(scenarios.group(1)) if scenarios else None,
            "evidence": name + "-result.txt",
        })
        print(f"PASS {name}: {assertions} assertions")

    def stores(self) -> None:
        """실제 조회·파서·비동기 서비스와 HTTP/파일/실행기 경계를 검증한다."""
        json_jar = Path(os.environ.get(
            "JSON_JAR", self.root / ".local-dev/test-dependencies/json-20240303.jar",
        ))
        if not json_jar.is_file():
            raise FileNotFoundError("Set JSON_JAR to an existing org.json runtime JAR")
        folder = self.root / "app/src/debug/java/com/example/pleosstarter/stores"
        excluded = {"StoreDetailDialog", "StoreResultsDialog", "StoreResultsPanel"}
        product = [source for source in folder.glob("*.java") if source.stem not in excluded]
        tests = sorted((self.source / "stores/src").rglob("*.java"))
        classes = self.compile("stores", product + tests, [json_jar])
        self.run("stores-api", "com.example.pleosstarter.stores.StoreApiProbe", [classes, json_jar])
        self.run("stores-filters", "com.example.pleosstarter.stores.TouristFilterProbe", [classes, json_jar])
        self.run("stores-proxy", "com.example.pleosstarter.stores.PlacesProxyProbe", [classes, json_jar])

    def navigation_classpath(self) -> list[Path]:
        """설치 버전과 같은 내비 모델·리스너·Kotlin 의존성을 구성한다."""
        return [
            self.android, self.sdk_jar("NaviHelper", "3.0.5"),
            self.sdk_jar("navi-data", "3.0.4"), self.sdk_jar("navi-aidl", "3.0.1"),
            self.artifact("org.jetbrains.kotlin", "kotlin-stdlib", "2.1.0", "jar"),
            self.artifact("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm", "1.6.3", "jar"),
            self.artifact("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm", "1.6.3", "jar"),
        ]

    def navigation(self) -> None:
        """실제 SDK DTO와 서비스·메인 큐 대역으로 목적지 요청 생명주기를 검증한다."""
        folder = self.root / "app/src/main/java/com/example/pleosstarter/navigation"
        names = ["NavigationDestination", "DestinationNavigationClient", "NavigationEvent",
                 "PleosDestinationNavigationClient"]
        dependencies = self.navigation_classpath()
        product = self.compile("navigation-product", [folder / (name + ".java") for name in names], dependencies)
        tests = self.compile("navigation-tests", sorted((self.source / "navigation/src").rglob("*.java")),
                             [product, *dependencies])
        self.run("navigation", "probe.SelectedNavigationProbe", [tests, product, *dependencies])

    def speech(self) -> None:
        """실제 STT 리스너와 지연 종료 대역으로 취소·재시도·타임아웃을 확인한다."""
        product_file = self.root / "app/src/main/java/com/example/pleosstarter/speech/PleosSpeechClient.java"
        dependencies = [self.android, self.sdk_jar("SpeechToText", "3.3.1")]
        product = self.compile("speech-product", [product_file], dependencies)
        tests = self.compile("speech-tests", sorted((self.source / "speech/src").rglob("*.java")),
                             [product, *dependencies])
        classpath = [tests, product, *dependencies]
        self.run("speech-lifecycle", "SpeechLifecycleProbe", classpath)
        self.run("speech-cancel-regression", "SpeechCancellationProbe", classpath, explicit_checks=2)
        self.run("speech-manual-stop-regression", "ManualStopWithoutEndProbe", classpath, explicit_checks=2)

    def location(self) -> None:
        """SDK 위치 공급의 정상·오류·시간 제한·구독 종료를 검증한다."""
        folder = self.root / "app/src/debug/java/com/example/pleosstarter/location"
        dependencies = self.navigation_classpath()
        product = self.compile("location-product", sorted(folder.glob("*.java")), dependencies)
        tests = self.compile("location-tests", sorted((self.source / "location/src").rglob("*.java")),
                             [product, *dependencies])
        self.run("location", "LocationSourceProbe", [tests, product, *dependencies])

    def save(self, status: str, error: str = "") -> None:
        """실행한 결과만 구조화하고 오래된 전체 필터 하니스는 별도로 명시한다."""
        red_green_file = self.source / "red_green.json"
        red_green = json.loads(red_green_file.read_text(encoding="utf-8")) if red_green_file.exists() else []
        result = {
            "status": status, "suites": self.results,
            "assertions": sum(int(row["assertions"]) for row in self.results),
            "redGreen": red_green, "error": error,
            "scope": "offline-current-product-sources-and-real-SDK-types",
            "excluded": ["live API", "microphone/Binder", "real vehicle", "UI 100m enforcement"],
            "retiredHarness": "KakaoNearbyApiProbe expects removed ALL category; replaced by TouristFilterProbe",
            "sourceHashes": self.source_hashes,
        }
        (self.output / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    """선택한 검증만 순차 실행하며 외부 호출·ADB·앱 데이터 변경은 하지 않는다."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--suite", choices=["all", "stores", "navigation", "speech", "location"], default="all")
    arguments = parser.parse_args()
    suite = IntegrationSuite(arguments.output)
    try:
        names = ["stores", "navigation", "speech", "location"] if arguments.suite == "all" else [arguments.suite]
        for name in names:
            getattr(suite, name)()
        suite.save("PASS")
        return 0
    except Exception as failure:
        suite.save("FAIL", str(failure))
        print(str(failure), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
