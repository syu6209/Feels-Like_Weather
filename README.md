# Feels-Like Weather

**나에게 맞는 체감온도**를 알려주는 안드로이드 날씨 앱.
Open-Meteo 날씨에 체감 설문 데이터를 쌓아 "내 체감 구간"을 보정하고, 그에 맞춰 체감 브리핑과 연령대별 옷차림을 추천합니다.

## 주요 기능

- **현재 위치 날씨**: 위치 권한 + Fused Location, 내장 Geocoder 로 상단바에 `지역명(도시명)의 날씨` 표시
- **상단바 메뉴(⋮)**: 지역 선택 / 설정
- **접히는 현재 날씨 헤더**: CollapsingToolbarLayout(exitUntilCollapsed) 방식, 스크롤 시 완전히 접히고 상단바에 요약 표시
- **그리드 카드 목록** (가로 600dp 이상 2열, 그 외 1열 · 라운드 코너 카드)
  1. **체감 보정 예보**: 큰 체감온도, 구간별 대표색, 흐르는 브러쉬 그라데이션 브리핑, 12시간 전~48시간 후 시간대 박스, ⓘ 구간 산정 그래프(기본/내 체감)
  2. **체감 설문**: 선택한 시간대의 체감 질문, 3개 버튼 + 양옆 모서리 보이는 가로 스크롤, 통통 튀는 애니메이션과 파티클, 응답으로 내 체감 구간 보정
  3. **옷차림 추천**: 프로필 칩(마지막 선택 기억), 오늘·내일·모레, 활동 시간 최저·평균·최고 체감과 구간 흐름, 구간이 바뀌는 날의 겹쳐 입기 안내(규칙 기반), 연령대·구간별 옷 이미지, 날씨 아이템은 필요할 때만 상단, ⓘ 추천 근거
  4. 시간별 예보 · 주간 예보 · 상세 날씨
- **프로필 편집**: 이름, 연령대 칩, 시간별 막대 그래프 위 핸들 드래그로 활동 시간 설정, 휴지통/저장
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
│   └── prefs/      AppPreferences (DataStore: 프로필, 설문, 브리핑 고정, 지역, 테마)
├── domain/         FeelsBand/Thresholds, FeelsLearner, Briefings, TimeSlots, Profile, Clothing
└── ui/
    ├── main/       MainViewModel, MainScreen, CollapsingHeader
    ├── cards/      체감 보정 예보, 체감 설문, 옷차림 추천, 날씨 카드
    ├── components/ BriefingText(브러쉬), ParticleBurst, BandGraph, 날씨 아이콘/배경
    ├── profile/    프로필 편집, 활동 시간 선택 그래프
    ├── region/     지역 선택
    └── settings/   설정
```

## 빌드 · 테스트

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

단위 테스트(도메인 로직)와 함께 Robolectric 스크린샷 테스트가 실행되어 `app/build/screenshots/*.png` 로 카드 렌더링 결과가 저장됩니다.

## 데이터 출처

- 날씨: [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0)
- 지역명: Android 내장 `Geocoder`
