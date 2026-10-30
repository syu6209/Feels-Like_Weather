# 옷차림 이미지 가이드

옷차림 추천 카드는 **인물 없이 옷/소품 이미지만** 나열합니다. 이미지가 아직 없으면 기본 아이콘(옷걸이 등)으로 표시되고,
아래 규칙대로 `app/src/main/res/drawable/` (또는 `drawable-nodpi/`)에 파일을 넣으면 **코드 수정 없이** 자동으로 사용됩니다.

## 파일 이름 규칙

탐색 순서 (`ClothingCard.kt` 의 `ClothingImage`):

1. `cloth_<연령대>_<key>.png` — 연령대 전용 이미지 (예: `cloth_infant_padding.png`)
2. `cloth_<key>.png` — 공용 이미지 (예: `cloth_umbrella.png`)
3. 둘 다 없으면 기본 아이콘

- 연령대: `infant`(유아), `child`(아동), `adult`(성인), `senior`(중장년)
- 파일명은 소문자·숫자·밑줄만 가능 (안드로이드 리소스 규칙)
- 권장: 512×512 PNG 또는 WebP, **투명 배경**, 정사각형, 아이템이 가운데 80% 영역 안에 오도록

## 공통 프롬프트 (Grok / Codex 등)

스타일을 맞추기 위해 모든 이미지에 같은 베이스 프롬프트를 쓰는 것을 권장합니다.

```
A single {ITEM} {AGE_HINT}, isolated product illustration, no person, no mannequin, no text,
soft flat 3D style, gentle studio lighting, pastel colors, centered, transparent background, square 1:1
```

## 연령대별 필요 이미지


### 유아 (`infant`) — 16개

| 파일명 | 아이템 | {ITEM} 예시 |
|---|---|---|
| `cloth_infant_romper.png` | 반팔 바디수트 | romper for a toddler (age 1-6) |
| `cloth_infant_sandals.png` | 샌들 | sandals for a toddler (age 1-6) |
| `cloth_infant_shorts.png` | 반바지 | shorts for a toddler (age 1-6) |
| `cloth_infant_long_romper.png` | 긴팔 우주복 | long romper for a toddler (age 1-6) |
| `cloth_infant_cardigan.png` | 가디건 | cardigan for a toddler (age 1-6) |
| `cloth_infant_sneakers.png` | 운동화 | sneakers for a toddler (age 1-6) |
| `cloth_infant_vest.png` | 경량 조끼 | vest for a toddler (age 1-6) |
| `cloth_infant_light_jacket.png` | 얇은 재킷·바람막이 | light jacket for a toddler (age 1-6) |
| `cloth_infant_sweatshirt.png` | 맨투맨·후드티 | sweatshirt for a toddler (age 1-6) |
| `cloth_infant_light_padding.png` | 경량 패딩 | light padding for a toddler (age 1-6) |
| `cloth_infant_beanie.png` | 털모자 | beanie for a toddler (age 1-6) |
| `cloth_infant_thermal.png` | 내복·발열내의 | thermal for a toddler (age 1-6) |
| `cloth_infant_snowsuit.png` | 방한 우주복 | snowsuit for a toddler (age 1-6) |
| `cloth_infant_neck_warmer.png` | 넥워머 | neck warmer for a toddler (age 1-6) |
| `cloth_infant_gloves.png` | 장갑 | gloves for a toddler (age 1-6) |
| `cloth_infant_winter_boots.png` | 방한화 | winter boots for a toddler (age 1-6) |

### 아동 (`child`) — 20개

| 파일명 | 아이템 | {ITEM} 예시 |
|---|---|---|
| `cloth_child_short_sleeve.png` | 반팔 티셔츠 | short sleeve for a child (age 7-12) |
| `cloth_child_shorts.png` | 반바지 | shorts for a child (age 7-12) |
| `cloth_child_sandals.png` | 샌들 | sandals for a child (age 7-12) |
| `cloth_child_sneakers.png` | 운동화 | sneakers for a child (age 7-12) |
| `cloth_child_cardigan.png` | 가디건 | cardigan for a child (age 7-12) |
| `cloth_child_cotton_pants.png` | 면바지 | cotton pants for a child (age 7-12) |
| `cloth_child_long_sleeve.png` | 얇은 긴팔 | long sleeve for a child (age 7-12) |
| `cloth_child_sweatshirt.png` | 맨투맨·후드티 | sweatshirt for a child (age 7-12) |
| `cloth_child_light_jacket.png` | 얇은 재킷·바람막이 | light jacket for a child (age 7-12) |
| `cloth_child_light_padding.png` | 경량 패딩 | light padding for a child (age 7-12) |
| `cloth_child_jeans.png` | 청바지 | jeans for a child (age 7-12) |
| `cloth_child_thermal.png` | 내복·발열내의 | thermal for a child (age 7-12) |
| `cloth_child_padding.png` | 패딩 | padding for a child (age 7-12) |
| `cloth_child_fleece_pants.png` | 기모 바지 | fleece pants for a child (age 7-12) |
| `cloth_child_beanie.png` | 털모자 | beanie for a child (age 7-12) |
| `cloth_child_gloves.png` | 장갑 | gloves for a child (age 7-12) |
| `cloth_child_winter_boots.png` | 방한화 | winter boots for a child (age 7-12) |
| `cloth_child_knit.png` | 니트 | knit for a child (age 7-12) |
| `cloth_child_long_padding.png` | 롱패딩 | long padding for a child (age 7-12) |
| `cloth_child_neck_warmer.png` | 넥워머 | neck warmer for a child (age 7-12) |

### 성인 (`adult`) — 23개

| 파일명 | 아이템 | {ITEM} 예시 |
|---|---|---|
| `cloth_adult_sleeveless.png` | 민소매 | sleeveless for an adult |
| `cloth_adult_short_sleeve.png` | 반팔 티셔츠 | short sleeve for an adult |
| `cloth_adult_shorts.png` | 반바지 | shorts for an adult |
| `cloth_adult_sandals.png` | 샌들 | sandals for an adult |
| `cloth_adult_linen_shirt.png` | 얇은 셔츠 | linen shirt for an adult |
| `cloth_adult_cotton_pants.png` | 면바지 | cotton pants for an adult |
| `cloth_adult_sneakers.png` | 운동화 | sneakers for an adult |
| `cloth_adult_long_sleeve.png` | 얇은 긴팔 | long sleeve for an adult |
| `cloth_adult_cardigan.png` | 가디건 | cardigan for an adult |
| `cloth_adult_sweatshirt.png` | 맨투맨·후드티 | sweatshirt for an adult |
| `cloth_adult_knit.png` | 니트 | knit for an adult |
| `cloth_adult_light_jacket.png` | 얇은 재킷·바람막이 | light jacket for an adult |
| `cloth_adult_jeans.png` | 청바지 | jeans for an adult |
| `cloth_adult_trench_coat.png` | 트렌치코트 | trench coat for an adult |
| `cloth_adult_coat.png` | 코트 | coat for an adult |
| `cloth_adult_thermal.png` | 내복·발열내의 | thermal for an adult |
| `cloth_adult_padding.png` | 패딩 | padding for an adult |
| `cloth_adult_fleece_pants.png` | 기모 바지 | fleece pants for an adult |
| `cloth_adult_scarf.png` | 목도리 | scarf for an adult |
| `cloth_adult_gloves.png` | 장갑 | gloves for an adult |
| `cloth_adult_winter_boots.png` | 방한화 | winter boots for an adult |
| `cloth_adult_long_padding.png` | 롱패딩 | long padding for an adult |
| `cloth_adult_beanie.png` | 털모자 | beanie for an adult |

### 중장년 (`senior`) — 18개

| 파일명 | 아이템 | {ITEM} 예시 |
|---|---|---|
| `cloth_senior_short_sleeve.png` | 반팔 티셔츠 | short sleeve for a middle-aged/older adult, comfortable fit |
| `cloth_senior_linen_shirt.png` | 얇은 셔츠 | linen shirt for a middle-aged/older adult, comfortable fit |
| `cloth_senior_cotton_pants.png` | 면바지 | cotton pants for a middle-aged/older adult, comfortable fit |
| `cloth_senior_sandals.png` | 샌들 | sandals for a middle-aged/older adult, comfortable fit |
| `cloth_senior_sneakers.png` | 운동화 | sneakers for a middle-aged/older adult, comfortable fit |
| `cloth_senior_long_sleeve.png` | 얇은 긴팔 | long sleeve for a middle-aged/older adult, comfortable fit |
| `cloth_senior_cardigan.png` | 가디건 | cardigan for a middle-aged/older adult, comfortable fit |
| `cloth_senior_knit.png` | 니트 | knit for a middle-aged/older adult, comfortable fit |
| `cloth_senior_vest.png` | 경량 조끼 | vest for a middle-aged/older adult, comfortable fit |
| `cloth_senior_light_jacket.png` | 얇은 재킷·바람막이 | light jacket for a middle-aged/older adult, comfortable fit |
| `cloth_senior_thermal.png` | 내복·발열내의 | thermal for a middle-aged/older adult, comfortable fit |
| `cloth_senior_coat.png` | 코트 | coat for a middle-aged/older adult, comfortable fit |
| `cloth_senior_fleece_pants.png` | 기모 바지 | fleece pants for a middle-aged/older adult, comfortable fit |
| `cloth_senior_scarf.png` | 목도리 | scarf for a middle-aged/older adult, comfortable fit |
| `cloth_senior_long_padding.png` | 롱패딩 | long padding for a middle-aged/older adult, comfortable fit |
| `cloth_senior_beanie.png` | 털모자 | beanie for a middle-aged/older adult, comfortable fit |
| `cloth_senior_gloves.png` | 장갑 | gloves for a middle-aged/older adult, comfortable fit |
| `cloth_senior_winter_boots.png` | 방한화 | winter boots for a middle-aged/older adult, comfortable fit |

### 날씨 아이템 (공용) — 8개

연령대 구분 없이 `cloth_<key>.png` 하나면 충분합니다. 아이용을 따로 만들고 싶다면 `cloth_child_raincoat.png` 처럼 연령대 접두사를 붙이세요.

| 파일명 | 아이템 | 표시 조건 |
|---|---|---|
| `cloth_umbrella.png` | 우산 | 활동 시간 강수확률 40%↑ 또는 강수 0.5mm↑ |
| `cloth_raincoat.png` | 우비 | (유아·아동) 우산이 필요할 때 |
| `cloth_rain_boots.png` | 장화 | 많은 비(10mm↑/강한 비) 또는 눈 |
| `cloth_sun_hat.png` | 모자 | 자외선 3↑ 또는 더운 날 |
| `cloth_sunglasses.png` | 선글라스 | 자외선 6↑ (유아 제외) |
| `cloth_sunscreen.png` | 선크림 | 자외선 3↑ |
| `cloth_water_bottle.png` | 물병 | 더움 이상 구간 |
| `cloth_hand_warmer.png` | 핫팩 | 추움 이하 구간 |

> 공용 이미지(`cloth_<key>.png`)만 먼저 만들어 넣고, 연령대별 이미지는 필요한 것부터 점진적으로 추가해도 됩니다.
