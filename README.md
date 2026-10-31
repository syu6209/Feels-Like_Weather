# Feels-Like Weather

**나에게 맞는 체감온도**를 알려주는 안드로이드 날씨 앱.
Open-Meteo 날씨에 체감 설문 데이터를 쌓아 "내 체감 구간"을 보정하고, 그에 맞춰 체감 브리핑과 연령대별 옷차림을 추천합니다.

## 주요 기능

- **현재 위치 날씨**: 위치 권한 + Fused Location, 내장 Geocoder 로 상단바에 `지역명(도시명)의 날씨` 표시
- **상단바 지역명**: 누르면 현재 위치 · 저장한 지역 드롭다운과 맨 아래 `지역 추가`, 저장한 지역이 없으면 바로 지역 선택 화면
- **상단바 메뉴(⋮)**: 지역 선택 / 설정
- **날씨 캐시 · 오프라인**: 받아온 7일치(오늘 포함, 지난 12시간 시간대용 어제 포함) 예보를 지역별로 로컬 DB(SQLite)에 저장
  - 앱을 열면 저장된 날씨를 바로 보여주고, 받은 지 30분이 지났을 때만 API 로 새로 받음(화면이 켜져 있는 동안 1분마다 확인, 당겨서 새로고침은 즉시)
  - 현재 위치가 3km 이상 바뀌면 30분이 지나지 않았어도 새로 받음
  - 오프라인이면 저장된 날씨를 보여주고, 시간이 지난 만큼 현재 날씨는 그 시각의 시간별 예보로 바꿔 보여줌
  - 받은 지 6시간이 지난 날씨에는 현재 날씨 아래에 `M월 d일 HH:mm 기준 날씨` 표시
  - 받은 지 5일이 지난 캐시는 만료되어 삭제
- **접히는 현재 날씨 헤더**: CollapsingToolbarLayout(exitUntilCollapsed) 방식, 스크롤 시 완전히 접히고 상단바에 요약 표시
- **그리드 카드 목록** (가로 600dp 이상 2열, 그 외 1열 · 라운드 코너 카드)
  1. **체감 보정 예보**: 큰 체감온도, 구간별 대표색, 흐르는 브러쉬 그라데이션 브리핑, 12시간 전~48시간 후 시간대 박스, ⓘ 구간 산정 그래프(기본/내 체감)
  2. **체감 설문**: 선택한 시간대의 체감 질문, 3개 버튼 + 양옆 모서리 보이는 가로 스크롤, 통통 튀는 애니메이션과 파티클, 응답으로 내 체감 구간 보정
  3. **옷차림 추천**: 프로필 칩(마지막 선택 기억), 오늘·내일·모레, 활동 시간 최저·평균·최고 체감과 구간 흐름, 구간이 바뀌는 날의 겹쳐 입기 안내(규칙 기반), 연령대·구간별 옷 이미지, 날씨 아이템은 필요할 때만 상단, ⓘ 추천 근거
  4. 시간별 예보 · 주간 예보 · 상세 날씨
- **프로필 편집**: 이름, 연령대 칩, 시간별 막대 그래프 위 핸들 드래그로 활동 시간 설정, 휴지통/저장
- **홈 화면 위젯** (Jetpack Glance)
  - **체감 보정 예보 위젯**: 체감온도·구간, 브리핑, 다음 시간대, 위젯에서 바로 답하는 체감 설문(설정에서 켜기/끄기)
  - **옷차림 추천 위젯**: 위젯마다 보여줄 프로필 선택, 활동 시간 체감 구간, 챙길 것, 옷 이미지
  - 크기 2×1 · 2×2 · 2×3 · 4×1 · 4×2 · 4×3 · 5×4 이상까지 크기별 배치(반응형), 크기를 늘릴수록 더 많은 정보
  - **위젯 설정**: 홈 화면에서 위젯을 길게 누른 뒤 `설정`(안드로이드 12+, 삼성 One UI 포함). 배경 투명도 0~100%, 글자색(자동/흰색/검정), 설문 표시, 프로필
  - 앱이 받은 예보를 캐시해 위젯이 그 데이터로 그리고, 30분마다 마지막 위치로 예보를 새로 받음(백그라운드 위치 권한 불필요)
- **설정**: 테마, 내 체감 그래프, 내 체감 설문 기록(삭제 · 7일 내 되돌리기), 체감 구간 초기화, 프로필 관리, 데이터 출처

## 문서

- [체감 구간·옷차림 추천 근거](docs/FEELS_BANDS.md)
- [옷차림 이미지 파일 규칙·생성 프롬프트](docs/CLOTHING_IMAGES.md)

## 구조

```
app/src/main/java/com/zozi/feels_likeweather/
├── data/
│   ├── model/      날씨 모델, WMO 날씨 코드
│   ├── remote/     OpenMeteoClient (HttpURLConnection + org.json)
│   ├── location/   LocationClient(Fused), PlaceNameResolver(Geocoder)
│   ├── local/      WeatherDatabase · WeatherStore (지역별 날씨 캐시, 만료 5일)
│   └── prefs/      AppPreferences (DataStore: 프로필, 설문, 브리핑 고정, 지역, 테마)
├── domain/         FeelsBand/Thresholds, FeelsLearner, Briefings, TimeSlots, Profile, Clothing
└── ui/
    ├── main/       MainViewModel, MainScreen, CollapsingHeader
    ├── cards/      체감 보정 예보, 체감 설문, 옷차림 추천, 날씨 카드
    ├── components/ BriefingText(브러쉬), ParticleBurst, BandGraph, 날씨 아이콘/배경
    ├── profile/    프로필 편집, 활동 시간 선택 그래프
    ├── region/     지역 선택
    └── settings/   설정
widget/             Glance 위젯(체감 보정 예보 · 옷차림 추천), 위젯 설정 화면, 30분 갱신 Worker
```

## 빌드 · 테스트

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

단위 테스트(도메인 로직)와 함께 Robolectric 스크린샷 테스트가 실행되어 `app/build/screenshots/*.png` 로 카드 · 위젯(크기별 · 설정별) 렌더링 결과가 저장됩니다.

## 데이터 출처

- 날씨: [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0)
- 지역명: Android 내장 `Geocoder`
