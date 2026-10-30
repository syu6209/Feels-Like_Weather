package com.zozi.feels_likeweather.ui.theme

import androidx.compose.ui.graphics.Color
import com.zozi.feels_likeweather.domain.FeelsBand

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/** 구간 대표 색상. */
val FeelsBand.composeColor: Color get() = Color(color)

/** 브러쉬 그라데이션에 함께 흐르는 보조 색상. */
val FeelsBand.highlightColor: Color get() = Color(highlight)

/** 대표 색상 위에 올리는 글자색. 밝은 대표색(노랑·하늘)에는 어두운 글자를 쓴다. */
val FeelsBand.onColor: Color
    get() = when (this) {
        FeelsBand.CHILLY, FeelsBand.WARM -> Color(0xFF1B1B1F)
        else -> Color.White
    }
