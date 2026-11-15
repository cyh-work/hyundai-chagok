"""실제 기록 서비스·저장소를 컴파일해 메모리 저장 경계에서 회귀 검증한다."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import urllib.request


JSON_VERSION = "20240303"
JSON_SHA256 = "3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed"
PROBES = ["FavoriteVoiceStorageProbe", "RecordRelocationProbe", "RecordDeleteProbe", "RecordRevisitProbe"]


def executable(root: Path, name: str) -> Path:
    """명시한 Java 환경을 우선 사용하고 프로젝트 개발 도구와 PATH를 차례로 확인한다."""
    suffix = ".exe" if os.name == "nt" else ""
    configured = os.environ.get("JAVA_HOME")
    if configured:
        candidate = Path(configured) / "bin" / (name + suffix)
        if not candidate.is_file():
            raise RuntimeError("JAVA_HOME의 Java 실행 파일을 찾지 못했습니다.")
        return candidate

    candidate = root / ".local-dev/jdk-17/bin" / (name + suffix)
    if candidate.is_file():
        return candidate
    installed = shutil.which(name)
    if installed:
        return Path(installed)
    raise RuntimeError("Java 17 이상 또는 JAVA_HOME 설정이 필요합니다.")


def android_jar(root: Path) -> Path:
    """제품 compileSdk와 같은 공개 Android 35 API만 컴파일 의존성으로 사용한다."""
    configured = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    sdk = Path(configured) if configured else root / ".local-dev/android-sdk"
    jar = sdk / "platforms/android-35/android.jar"
    if not jar.is_file():
        raise RuntimeError("Android SDK Platform 35와 ANDROID_HOME 설정이 필요합니다.")
    return jar


def fingerprint(path: Path) -> str:
    """실행 대상 소스와 외부 의존성의 정확한 버전을 기록한다."""
    return hashlib.sha256(path.read_bytes()).hexdigest()


def json_jar(root: Path, output: Path) -> Path:
    """고정 버전·해시의 JSON-java를 재사용하거나 공식 Maven 저장소에서 받는다."""
    configured = os.environ.get("JSON_JAR") or os.environ.get("JSON_JAVA_JAR")
    candidates = [
        Path(configured) if configured else None,
        root / ".local-dev/test-dependencies" / f"json-{JSON_VERSION}.jar",
        output / "dependencies" / f"json-{JSON_VERSION}.jar",
    ]
    for candidate in candidates:
        if candidate and candidate.is_file():
            if fingerprint(candidate) != JSON_SHA256:
                raise RuntimeError("JSON-java 의존성 해시가 고정 버전과 다릅니다.")
            return candidate
    if configured:
        raise RuntimeError("JSON_JAVA_JAR에서 지정한 파일을 찾지 못했습니다.")

    destination = candidates[-1]
    destination.parent.mkdir(parents=True, exist_ok=True)
    url = f"https://repo.maven.apache.org/maven2/org/json/json/{JSON_VERSION}/json-{JSON_VERSION}.jar"
    with urllib.request.urlopen(url, timeout=30) as response:
        downloaded = response.read()
    if hashlib.sha256(downloaded).hexdigest() != JSON_SHA256:
        raise RuntimeError("받은 JSON-java 파일의 해시 검증에 실패했습니다.")
    destination.write_bytes(downloaded)
    return destination


def product_sources(root: Path) -> list[Path]:
    """복사본이 아닌 현재 제품의 기록·히스토리·암호화 저장 구현을 선택한다."""
    package = "com/example/pleosstarter"
    debug = root / "app/src/debug/java" / package / "records"
    main = root / "app/src/main/java" / package / "notes"
    sources = [debug / name for name in (
        "SavedMapPoint.java", "MapRecordsService.java", "LocalMapRecordsService.java",
        "SharedPreferencesFavoriteRepository.java", "FavoriteRepository.java", "MapRecordFailure.java",
        "MapHistoryService.java", "MapHistoryEntry.java", "HistoryMetadataRepository.java",
        "HistoryMetadataFailure.java", "SharedPreferencesHistoryMetadataRepository.java",
        "RecordRevisitService.java", "LocalRecordRevisitService.java",
    )]
    sources.extend(main / name for name in (
        "LocationNote.java", "LocationNotes.java", "LocationNotesException.java", "LocationNoteRepository.java",
        "LocationReentryTracker.java", "LocationNoteCipher.java", "AndroidKeyStoreLocationNoteCipher.java",
        "SharedPreferencesLocationNoteRepository.java",
    ))
    sources.append(root / "app/src/debug/java" / package / "kakao/MapRecordMarker.java")
    return sources


def execute(command: list[str | Path], output: Path) -> subprocess.CompletedProcess:
    """도구 출력을 지정 로그에 보관하며 실패를 성공 결과로 바꾸지 않는다."""
    result = subprocess.run(list(map(str, command)), capture_output=True, text=True, encoding="utf-8")
    output.write_text(result.stdout + result.stderr, encoding="utf-8")
    return result


def main() -> int:
    """공개 서비스 계약의 기존 회귀와 신규 결함 재현을 같은 제품 버전으로 실행한다."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path)
    parser.add_argument("--probe", choices=PROBES)
    arguments = parser.parse_args()
    here = Path(__file__).resolve().parent
    root = here.parents[1]
    output = arguments.output or root / "build/test-results/records/current"
    output = output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    classes = output / "classes"
    classes.mkdir(exist_ok=True)

    sources = product_sources(root)
    before = {str(path.relative_to(root)): fingerprint(path) for path in sources}
    android = android_jar(root)
    dependency = json_jar(root, output)
    selected = [arguments.probe] if arguments.probe else PROBES
    compiled = execute([
        executable(root, "javac"), "-encoding", "UTF-8", "-cp", android, "-d", classes,
        *sources, *(here / (probe + ".java") for probe in selected),
    ], output / "compile.txt")

    suites = []
    if compiled.returncode == 0:
        classpath = os.pathsep.join(map(str, [classes, dependency, android]))
        for probe in selected:
            result = execute([
                executable(root, "java"), "-cp", classpath,
                "com.example.pleosstarter.notes." + probe,
            ], output / (probe + ".txt"))
            count = re.search(r"assertions=(\d+)", result.stdout)
            suites.append({
                "name": probe,
                "status": "PASS" if result.returncode == 0 else "FAIL",
                "assertions": int(count.group(1)) if count else 0,
                "exitCode": result.returncode,
            })
            print(result.stdout + result.stderr, end="")
    else:
        print(compiled.stderr, end="")

    after = {str(path.relative_to(root)): fingerprint(path) for path in sources}
    passed = compiled.returncode == 0 and all(suite["status"] == "PASS" for suite in suites) and before == after
    result = {
        "status": "PASS" if passed else "FAIL",
        "suites": suites,
        "assertions": sum(suite["assertions"] for suite in suites),
        "compileExitCode": compiled.returncode,
        "sourceChangedDuringRun": before != after,
        "sourceSha256": before,
        "testSha256": {probe: fingerprint(here / (probe + ".java")) for probe in selected},
        "boundary": "Actual record/history/revisit services and JSON repositories; memory persistence, fixed clock and JCE key boundary. Revisit uses actual marker snapshots and positions. No Android KeyStore, device disk, microphone or UI claim.",
        "redGreen": [],
    }
    if "RecordRevisitProbe" in selected:
        revisit_passed = any(suite["name"] == "RecordRevisitProbe" and suite["status"] == "PASS"
                             for suite in suites)
        revisit_red_green = json.loads((here / "revisit_red_green.json").read_text(encoding="utf-8"))
        for evidence in revisit_red_green:
            result["redGreen"].append({**evidence, "currentGreen": revisit_passed})
        result["initiallyGreenRevisitAssertions"] = 13
    (output / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
