# 빌드와 테스트

Java 17, Android SDK Platform 35, Python 3.10 이상이 필요합니다. Gradle 8.11.1은 포함된 wrapper가 준비합니다. Android Gradle Plugin은 8.9.2이며 앱의 최소 Android 버전은 28, targetSdk는 34입니다. SDK와 Maven 의존성을 처음 받으려면 인터넷 연결이 필요합니다.

## 개발 환경

`JAVA_HOME`을 JDK 17 설치 위치, `ANDROID_HOME`을 Android SDK 설치 위치로 설정합니다. 자동 테스트에서는 `GRADLE_USER_HOME`도 현재 사용하는 Gradle 캐시 위치로 지정합니다. Android SDK Build Tools는 Android Gradle Plugin이 요구하는 버전을 설치합니다. 개인 PC 경로는 저장소에 넣지 않습니다.

Windows PowerShell에서는 `./gradlew.bat`, macOS/Linux에서는 `sh ./gradlew`를 사용합니다. 아래 명령은 PowerShell 기준입니다.

```powershell
./gradlew.bat :app:assembleDebug :app:lintDebug
```

키를 넣지 않아도 debug APK는 컴파일됩니다. 실행 화면에는 카카오 앱 키 등록 안내가 나오며 지도는 초기화되지 않습니다. 이것은 API 인증 성공이나 실제 기기 동작을 검증하는 빌드가 아닙니다.

## 지도 설정

`config/kakao.properties.example`을 `.local-dev/kakao.properties`로 복사하고 본인 앱의 네이티브 앱 키를 설정합니다. 카카오 개발자 설정에는 패키지 `com.example.pleosstarter`와 사용하는 서명 인증서의 키 해시를 등록해야 합니다. debug와 release의 서명 인증서는 다릅니다.

네이티브 지도 앱 키는 SDK 실행을 위해 APK에 포함됩니다. REST API 비밀 키와 구분하고 카카오 앱의 플랫폼·서명 제한을 설정해야 합니다. 이 저장소에는 작성자의 지도 키, REST 키, 서명 키를 포함하지 않습니다.

지도는 Kakao Mobility KNSDK `1.12.8-hotfix05`를 사용합니다. Pleos 위치·기본 내비 연계에는 NaviHelper `3.0.5`, 음성 입력에는 SpeechToText `3.3.1`을 사용합니다. 의존성 저장소는 `settings.gradle`에 선언되어 있습니다. SDK 사용 자격·약관과 서비스 이용 설정은 각 제공자의 기준을 따릅니다. SDK 바이너리는 소스 저장소에 재배포하지 않습니다.

## 주변 장소 조회

release는 외부 HTTPS 장소 서비스만 사용합니다. `config/release-endpoint.properties.example`을 `.local-dev/release-endpoint.properties`로 복사해 본인이 준비한 공개 서비스의 기본 주소를 지정합니다. 주소가 없으면 지도 기능은 동작하지만 장소 조회는 설정 안내와 함께 차단됩니다. REST 키 파일로 우회하지 않습니다.

클라이언트 계약은 다음과 같습니다.

- 요청: `GET {base}/v1/places?category=FD6|CE7|CS2|AT4&latitude=...&longitude=...`
- 조회 반경: 100m 고정. 요청에는 인증 헤더나 REST 키를 넣지 않습니다.
- 성공: Kakao Local 형식의 `documents`와 `meta`를 가진 JSON.
- 실패: HTTP 400/404/405/429/502/503/504와 `{"error":{"code":"...","message":"..."}}`. 응답 원문을 앱 화면에 노출하지 않습니다.
- HTTPS만 허용하며 리다이렉트는 따라가지 않습니다. 기본 주소의 단순 경로는 허용하지만 사용자정보·쿼리·fragment·IP 주소·경로 순회는 허용하지 않습니다.

서버 구현, 호스팅 설정, 내부 네트워크 정보와 운영 인증정보는 공개 범위에 포함하지 않습니다. 계약 테스트는 가짜 응답을 사용하므로 서버 없이 실행할 수 있습니다. 실제 가게를 보려면 계약에 맞는 서버가 필요합니다.

debug에는 개발용 직접 REST 조회 경로가 남아 있습니다. 이 경로는 앱 전용 저장 공간의 `store-api.properties`를 읽으며 파일이 없으면 설정 오류를 표시합니다. 인증 파일과 키를 소스·리소스·APK에 추가하지 마세요. debug에서의 직접 조회와 release 중계 조회는 서로 다른 경로입니다.

## release APK

본인의 서명 키를 생성하고 `config/release-signing.properties.example`을 `.local-dev/release-signing.properties`로 복사해 설정합니다. 원본 앱의 개인 서명 키는 제공하지 않습니다. 다른 서명으로 만든 APK는 기존 설치본을 업데이트할 수 없습니다.

```powershell
./gradlew.bat :app:assembleRelease :app:lintRelease
```

네이티브 앱 키 또는 개인 서명 설정이 없으면 release 작업은 실패하도록 되어 있습니다. 기본 산출물은 `app/build/outputs/apk/release/`에 생성됩니다. 장소 서비스 주소는 별도 선택 설정이므로 빌드 성공과 장소 조회 성공을 구분해야 합니다. release는 `debuggable=false`이며 코드 축소는 SDK 규칙 검증 전까지 사용하지 않습니다.

실제 위치·내비·STT 동작은 해당 서비스가 제공되는 Pleos 환경에서 확인해야 합니다. 일반 Android 에뮬레이터의 컴파일·실행만으로 이를 확인할 수 없습니다.

## 자동 계약 테스트

먼저 `assembleDebug`를 실행해 지도 화면 조정자 테스트에 필요한 컴파일 결과를 만듭니다. `JAVA_HOME`, `ANDROID_HOME`, `GRADLE_USER_HOME`은 앞서 빌드에 사용한 도구를 가리켜야 합니다.

기록 테스트는 고정된 JSON-java `20240303`을 Maven Central에서 받고 SHA-256을 검사합니다. 먼저 이 테스트를 실행한 뒤 생성된 JAR 경로를 `JSON_JAR`로 지정하면 전체 테스트가 같은 파일을 재사용합니다.

```powershell
python -X utf8 tests/records/run.py --output build/test-results/records-bootstrap
$env:JSON_JAR = (Resolve-Path build/test-results/records-bootstrap/dependencies/json-20240303.jar).Path
python -X utf8 tests/run_all.py
```

이미 `JSON_JAR`에 검증된 JAR를 지정했다면 bootstrap 단계는 생략할 수 있습니다. 전체 실행기는 기록·히스토리·재방문, 입력·카메라·시험 이동, 장소·내비·위치·음성 수명주기를 검사합니다. 연동 테스트는 빌드가 내려받은 정확한 SDK 버전의 Gradle 캐시를 사용하며 외부 API·ADB·마이크를 호출하지 않습니다.

테스트 결과는 무시되는 `build/test-results/` 아래 생성됩니다. 과거 개인 환경의 RED/GREEN 로그와 실행 증거는 포함하지 않으며, 공개 테스트는 현재 코드에 대해 실제 실행한 결과만 생성합니다. 자동 테스트 통과는 실제 지도 인증·STT 발화·실차 검증을 대신하지 않습니다.

## 소스 구성

`app/src/main`은 기록 저장·내비·음성 등의 공통 코드, `app/src/debug/java`와 `res`는 현재 지도 제품 화면입니다. 기존 테스트 경로를 유지하기 위해 release source set도 이 Java·리소스를 참조합니다. 공개 후보의 debug manifest는 제품과 같은 진입점을 사용하며 과거 오버레이 실험은 제외했습니다.

기존 비활성 지도 화면용 `assets` 데이터는 OpenStreetMap에서 얻은 자료이며 파일 안의 출처와 ODbL 표시를 유지합니다. 현재 제품 화면은 카카오 지도를 사용합니다. Gradle wrapper의 기존 라이선스 고지도 유지합니다. 이 문서가 프로젝트나 타사 자산에 새로운 라이선스를 부여하지는 않습니다.
