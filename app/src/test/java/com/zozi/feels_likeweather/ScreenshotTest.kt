package com.zozi.feels_likeweather

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.zozi.feels_likeweather.data.model.WeatherForecast
import com.zozi.feels_likeweather.data.prefs.ThemeMode
import com.zozi.feels_likeweather.data.remote.OpenMeteoClient
import com.zozi.feels_likeweather.domain.Briefings
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.FeelsLearner
import com.zozi.feels_likeweather.domain.FeelsThresholds
import com.zozi.feels_likeweather.domain.Profile
import com.zozi.feels_likeweather.domain.SurveyRecord
import com.zozi.feels_likeweather.ui.cards.ClothingCard
import com.zozi.feels_likeweather.ui.cards.DailyForecastCard
import com.zozi.feels_likeweather.ui.cards.FeelsForecastCard
import com.zozi.feels_likeweather.ui.cards.FeelsSurveyCard
import com.zozi.feels_likeweather.ui.cards.HourlyForecastCard
import com.zozi.feels_likeweather.ui.cards.WeatherDetailsCard
import com.zozi.feels_likeweather.ui.components.BandGraph
import com.zozi.feels_likeweather.ui.components.skyBrush
import com.zozi.feels_likeweather.ui.main.CurrentWeatherHeader
import com.zozi.feels_likeweather.ui.main.FeelsUi
import com.zozi.feels_likeweather.ui.main.SurveyReaction
import com.zozi.feels_likeweather.ui.settings.SurveyHistoryContent
import com.zozi.feels_likeweather.ui.main.buildSlots
import com.zozi.feels_likeweather.ui.profile.ActivityRangeSelector
import com.zozi.feels_likeweather.ui.theme.FeelsLikeWeatherTheme
import com.zozi.feels_likeweather.ui.theme.composeColor
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * UI 를 PNG 로 렌더링해 레이아웃을 눈으로 확인하기 위한 테스트.
 * 결과물: app/build/screenshots/\*.png  (검증(assert)은 하지 않는다)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1800dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val forecast: WeatherForecast = OpenMeteoClient.parseForecast(
        JSONObject(javaClass.classLoader!!.getResource("forecast_seoul.json").readText()),
        fetchedAtMillis = 0L,
    )

    private fun feels(
        mine: FeelsThresholds = FeelsThresholds.DEFAULT,
        selectIndexFromCurrent: Int = 0,
        answerCurrent: FeelsBand? = null,
        reacting: Boolean = false,
    ): FeelsUi {
        val now = forecast.current.time
        val plain = buildSlots(forecast, now, mine)
        val current = plain.indexOfFirst { it.slot.isCurrent }
        val records = listOfNotNull(
            SurveyRecord(plain[current - 1].slot.key, plain[current - 1].apparent, FeelsBand.WARM, 1, plain[current - 1].myBand),
            answerCurrent?.let { SurveyRecord(plain[current].slot.key, plain[current].apparent, it, 2, plain[current].myBand) },
        )
        val slots = buildSlots(forecast, now, mine, records)
        val selected = slots[(current + selectIndexFromCurrent).coerceIn(slots.indices)]
        return FeelsUi(
            now = now,
            slots = slots,
            selected = selected,
            briefing = Briefings.pick(selected.displayBand, null, 0L, Random(3)).first,
            myThresholds = mine,
            answers = records.associate { it.slotKey to it.reported },
            reaction = if (reacting && answerCurrent != null) {
                SurveyReaction(selected.slot.key, selected.myBand, answerCurrent, Briefings.reaction(answerCurrent, true))
            } else null,
        )
    }

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            FeelsLikeWeatherTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColor = false) {
                content()
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(5_000)
        val view = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        // 실제 그려진 콘텐츠 높이만큼만 자른다.
        val height = compose.onRoot().fetchSemanticsNode().children.maxOfOrNull { it.boundsInRoot.bottom.toInt() }
            ?.coerceIn(1, view.height) ?: view.height
        val bitmap = Bitmap.createBitmap(view.width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Page(content: @Composable () -> Unit) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(skyBrush(forecast.current.weatherCode, forecast.current.isDay))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }

    @Test
    fun header() = shot("01_header") {
        Page { CurrentWeatherHeader(forecast, Modifier.padding(vertical = 40.dp)) }
    }

    @Test
    fun feelsForecastCard() = shot("02_feels_forecast") {
        Page { FeelsForecastCard(feels(), surveyCount = 0, onSelectSlot = {}) }
    }

    @Test
    fun feelsForecastCardDark() = shot("02b_feels_forecast_dark", dark = true) {
        Page { FeelsForecastCard(feels(selectIndexFromCurrent = 2), surveyCount = 3, onSelectSlot = {}) }
    }

    @Test
    fun surveyCard() = shot("03_survey") {
        Page {
            FeelsSurveyCard(feels(), onAnswer = {})
            FeelsSurveyCard(feels(selectIndexFromCurrent = -1), onAnswer = {})
            FeelsSurveyCard(feels(selectIndexFromCurrent = 2), onAnswer = {})
        }
    }

    @Test
    fun clothingCard() = shot("04_clothing") {
        Page {
            val f = feels()
            ClothingCard(f, forecast, Profile.defaults, Profile.MINE_ID, {}, {}, {})
            ClothingCard(f, forecast, Profile.defaults, "default-infant", {}, {}, {})
        }
    }

    @Test
    fun weatherCards() = shot("05_weather_cards") {
        Page {
            HourlyForecastCard(forecast)
            DailyForecastCard(forecast)
            WeatherDetailsCard(forecast)
        }
    }

    @Test
    fun feelsForecastCorrected() = shot("07_feels_corrected") {
        Page {
            // 반응 문구가 보이는 2초 동안
            FeelsForecastCard(feels(answerCurrent = FeelsBand.COLD, reacting = true), surveyCount = 2, onSelectSlot = {})
            // 반응 이후: 새 구간 브리핑 + "선선함 → 추움" 태그
            FeelsForecastCard(feels(answerCurrent = FeelsBand.COLD), surveyCount = 2, onSelectSlot = {})
            FeelsSurveyCard(feels(answerCurrent = FeelsBand.COLD), onAnswer = {})
        }
    }

    @Test
    fun surveyHistory() = shot("08_survey_history") {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        val records = listOf(
            SurveyRecord("2026-10-30T15:00", 15.4, FeelsBand.COLD, now - 1000, FeelsBand.COOL),
            SurveyRecord("2026-10-30T12:00", 16.2, FeelsBand.WARM, now - 3 * 3600_000, FeelsBand.COOL),
            SurveyRecord("2026-10-29T21:00", 11.0, FeelsBand.CHILLY, now - day, FeelsBand.CHILLY),
            SurveyRecord("2026-10-29T08:00", 7.5, FeelsBand.COLD, now - 2 * day, FeelsBand.CHILLY, deletedAtMillis = now - 2 * day),
        )
        androidx.compose.material3.Surface {
            SurveyHistoryContent(
                records = records,
                thresholds = FeelsLearner.estimate(records, now),
                nowMillis = now,
                onDelete = {},
                onRestore = {},
            )
        }
    }

    @Test
    fun clothingCardTomorrow() = shot("09_clothing_tomorrow") {
        Page {
            // 활동이 끝난 저녁 시간대 프로필 → 기본 선택이 '내일'
            val evening = Profile("p", "회사원", com.zozi.feels_likeweather.domain.AgeGroup.ADULT, 9, 15)
            ClothingCard(feels(), forecast, listOf(Profile.defaults.first(), evening), "p", {}, {}, {})
        }
    }

    @Test
    fun graphs() = shot("06_graphs") {
        val mine = FeelsLearner.estimate(
            listOf(
                SurveyRecord("a", 18.0, FeelsBand.WARM, 1),
                SurveyRecord("b", 18.5, FeelsBand.WARM, 2),
                SurveyRecord("c", 5.0, FeelsBand.COLD, 3),
            ),
            nowMillis = 3,
        )
        androidx.compose.material3.Surface {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                BandGraph(FeelsThresholds.DEFAULT, marker = 15.0)
                BandGraph(mine, marker = 15.0)
                ActivityRangeSelector(
                    start = 8,
                    end = 20,
                    onChange = { _, _ -> },
                    hourlyApparent = (0 until 24).map { 8.0 + 8 * kotlin.math.sin((it - 8) / 24.0 * Math.PI * 2) },
                    barColor = { FeelsThresholds.DEFAULT.bandOf(8.0 + 8 * kotlin.math.sin((it - 8) / 24.0 * Math.PI * 2)).composeColor },
                )
            }
        }
    }
}
