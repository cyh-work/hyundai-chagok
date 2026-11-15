"""현재 제품 소스로 입력·카메라 계산·시험 재생 계약을 독립 실행한다."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess


class InteractionSuite:
    """제품 소스를 매 실행에 컴파일하고 실행 경계와 결과를 함께 보관한다."""

    def __init__(self, output: Path):
        """테스트 소유 경로와 사용 가능한 Java 도구를 확정한다."""
        self.folder = Path(__file__).resolve().parent
        self.root = self.folder.parents[1]
        self.output = output.resolve()
        self.output.mkdir(parents=True, exist_ok=True)
        configured = os.environ.get("JAVA_HOME")
        java_home = Path(configured) if configured else self.root / ".local-dev/jdk-17"
        suffix = ".exe" if os.name == "nt" else ""
        self.compiler = java_home / "bin" / ("javac" + suffix)
        self.runtime = java_home / "bin" / ("java" + suffix)
        if not self.compiler.is_file() or not self.runtime.is_file():
            raise RuntimeError("JAVA_HOME에 Java 17 이상 개발 도구를 지정해 주세요.")
        self.suites = []
        self.hashes = {}

    def product(self, relative: str) -> Path:
        """검증하는 현재 제품 파일을 명시하고 해시를 결과에 남긴다."""
        source = self.root / relative
        self.hashes[relative] = hashlib.sha256(source.read_bytes()).hexdigest()
        return source

    def compile(self, name: str, sources: list[Path], classpath: list[Path] | None = None) -> Path:
        """검사 전용 디렉터리에 소스 복사본과 새 클래스만 생성한다."""
        work = self.output / name
        classes = work / "classes"
        classes.mkdir(parents=True, exist_ok=True)
        normalized = work / "sources"
        normalized.mkdir(exist_ok=True)

        # BOM 처리만 통일하며 원래 제품 파일은 변경하지 않는다.
        compiled_sources = []
        for index, source in enumerate(sources):
            destination = normalized / str(index) / source.name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(source.read_text(encoding="utf-8-sig"), encoding="utf-8")
            compiled_sources.append(destination)
        arguments = work / "javac.args"
        values = ["-encoding", "UTF-8", "--release", "17", "-d", str(classes)]
        if classpath:
            values.extend(["-cp", os.pathsep.join(str(path) for path in classpath)])
        values.extend(str(source) for source in compiled_sources)
        arguments.write_text("\n".join('"' + value.replace("\\", "/") + '"' for value in values),
                             encoding="utf-8")
        result = subprocess.run([str(self.compiler), "@" + str(arguments)],
                                capture_output=True, text=True, encoding="utf-8", errors="replace")
        (work / "compile.log").write_text(result.stdout + result.stderr, encoding="utf-8")
        if result.returncode:
            raise RuntimeError(name + " 컴파일 실패: " + result.stderr)
        return classes

    def execute(self, name: str, classes: Path, entry: str, boundary: str,
                dependencies: list[Path] | None = None) -> None:
        """공개 실행 진입점의 종료 코드·단언 수를 수집하고 중복 집계를 피한다."""
        classpath = os.pathsep.join(str(path) for path in [classes, *(dependencies or [])])
        result = subprocess.run([str(self.runtime), "-cp", classpath, entry],
                                capture_output=True, text=True, encoding="utf-8", errors="replace")
        transcript = result.stdout + result.stderr
        (self.output / (name + ".log")).write_text(transcript, encoding="utf-8")
        matches = re.findall(r"assertions=(\d+)", transcript)
        assertions = int(matches[-1]) if matches else 0
        scenarios = re.findall(r"scenarios=(\d+)", transcript)
        item = {"name": name, "status": "PASS" if result.returncode == 0 else "FAIL",
                "assertions": assertions, "boundary": boundary}
        if scenarios:
            item["scenarios"] = int(scenarios[-1])
        self.suites.append(item)
        print(transcript.strip())

    def run(self) -> dict:
        """입력 수명·두 손가락 변환·재생 시간을 서로 다른 실제 제품 경계에서 검사한다."""
        gesture_folder = "app/src/debug/java/com/example/pleosstarter/gesture/"
        main_map = "app/src/main/java/com/example/pleosstarter/map/"
        sources = list((self.folder / "boundary").rglob("*.java"))
        sources.extend(self.product(gesture_folder + name + ".java") for name in
                       ["MapGestureInputLayout", "StrokeTrailView", "MouseChordAdapter",
                        "AutomaticGestureArbiter", "MapStrokeGesture"])
        sources.extend(self.product(main_map + name + ".java") for name in
                       ["GestureClassifier", "GeometricGestureClassifier", "MapGesture",
                        "MapPlace", "StrokePoint"])
        names = ["FingerRoutingProbe", "RecordReadInputProbe", "DragMouseRoutingProbe"]
        sources.extend(self.folder / "probes" / (name + ".java") for name in names)
        classes = self.compile("input", sources)
        input_classes = classes
        for name in names:
            self.execute(name, classes, "com.example.pleosstarter.gesture." + name,
                         "실제 입력·분류기; Android 이벤트/뷰 경계 대역, 실제 SDK 렌더링 제외")

        transform = self.product("app/src/debug/java/com/example/pleosstarter/kakao/TwoFingerCameraTransform.java")
        classes = self.compile("camera", [transform, self.folder / "probes/TwoFingerCameraProbe.java"])
        self.execute("TwoFingerCameraProbe", classes,
                     "com.example.pleosstarter.kakao.TwoFingerCameraProbe",
                     "실제 좌표 변환 계산; Android/지도 SDK 카메라 적용 제외")

        revisit_sources = [self.product("app/src/debug/java/com/example/pleosstarter/" + name + ".java")
                           for name in ["records/RecordRevisitService", "records/LocalRecordRevisitService",
                                        "records/MapRecordFailure", "kakao/MapRecordMarker",
                                        "kakao/MapRevisitCoordinator", "ui/MapRevisitDisplay",
                                        "simulation/PlaybackScheduler"]]
        revisit_sources.extend(self.product("app/src/main/java/com/example/pleosstarter/notes/" + name + ".java")
                               for name in ["LocationNote", "LocationNotesException"])
        revisit_sources.append(self.folder / "probes/MapRevisitFlowProbe.java")
        classes = self.compile("revisit-flow", revisit_sources)
        revisit_classes = classes
        self.execute("MapRevisitFlowProbe", classes,
                     "com.example.pleosstarter.kakao.MapRevisitFlowProbe",
                     "실제 재방문 서비스와 화면 연결; 표시·예약 경계 대역")

        simulation = "app/src/debug/java/com/example/pleosstarter/simulation/"
        names = ["KakaoRouteGeometryMapper", "PlaybackClock", "PlaybackFailure", "PlaybackFrame",
                 "PlaybackListener", "PlaybackScheduler", "PlaybackState", "RoutePath",
                 "RoutePlaybackController", "RoutePoint", "RouteSource"]
        sources = [self.product(simulation + name + ".java") for name in names]
        sources.append(self.folder / "probes/RoutePlaybackProbe.java")
        classes = self.compile("playback", sources)
        self.execute("RoutePlaybackProbe", classes, "RoutePlaybackProbe",
                     "실제 경로·재생기; 외부 경로 공급과 가상 시계/예약 경계 대역")

        sdk_root = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
                        or self.root / ".local-dev/android-sdk")
        android_api = sdk_root / "platforms/android-35/android.jar"
        app_classes = self.root / "app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"
        if not android_api.is_file() or not app_classes.is_dir():
            raise RuntimeError("Android 35 SDK와 assembleDebug 결과가 있어야 화면 연결 계약을 검사할 수 있습니다.")
        dependencies = [input_classes, revisit_classes, app_classes, android_api]
        coordinator_sources = [source for source in sources if source.name != "RoutePlaybackProbe.java"]
        coordinator_sources.extend(self.product("app/src/debug/java/com/example/pleosstarter/kakao/" + name + ".java")
                                   for name in ["MapInteractionCoordinator", "MapRecordCoordinator",
                                                "PlaceMapSession", "KakaoPlaceSelection"])
        coordinator_sources.extend((self.folder / "coordinator-boundary").rglob("*.java"))
        coordinator_sources.append(self.folder / "probes/MapFollowPlaybackProbe.java")
        classes = self.compile("map-follow", coordinator_sources, dependencies)
        self.execute("MapFollowPlaybackProbe", classes,
                     "com.example.pleosstarter.kakao.MapFollowPlaybackProbe",
                     "실제 지도·기록 조정자와 재생기; 지도 SDK/표시/시계 경계 대역, 저장·모달 렌더링 제외",
                     dependencies)

        result = {"status": "PASS" if all(row["status"] == "PASS" for row in self.suites) else "FAIL",
                  "suites": self.suites, "assertions": sum(row["assertions"] for row in self.suites),
                  "redGreen": json.loads((self.folder / "revisit_red_green.json").read_text(encoding="utf-8")),
                  "sourceHashes": self.hashes,
                  "note": "기존 회귀 검증과 새 재방문 기능의 RED/GREEN 근거를 구분한다. 과거 구현을 TDD로 소급 주장하지 않는다."}
        return result


def main() -> None:
    """공통 실행기가 지정한 출력 위치에 기계 판독 결과를 남긴다."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path,
                        default=Path(__file__).resolve().parents[2] / "build/test-results/interaction")
    arguments = parser.parse_args()
    suite = InteractionSuite(arguments.output)
    try:
        result = suite.run()
    except Exception as failure:
        result = {"status": "FAIL", "suites": suite.suites,
                  "assertions": sum(row["assertions"] for row in suite.suites),
                  "redGreen": [], "error": str(failure), "sourceHashes": suite.hashes}
        print(str(failure))
    (suite.output / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    raise SystemExit(0 if result["status"] == "PASS" else 1)


if __name__ == "__main__":
    main()
