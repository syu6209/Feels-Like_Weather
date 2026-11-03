package com.zozi.feels_likeweather

import android.annotation.SuppressLint
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zozi.feels_likeweather.domain.AgeGroup
import com.zozi.feels_likeweather.domain.ClothingItem
import com.zozi.feels_likeweather.domain.ClothingRecommender
import com.zozi.feels_likeweather.domain.FeelsBand
import com.zozi.feels_likeweather.domain.imageNames
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 추천될 수 있는 모든 옷·날씨 아이템이 연령대마다 이미지를 찾는지(기본 아이콘으로 떨어지지 않는지) 확인한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClothingImagesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @SuppressLint("DiscouragedApi")
    private fun hasImage(item: ClothingItem, age: AgeGroup): Boolean =
        item.imageNames(age).any { context.resources.getIdentifier(it, "drawable", context.packageName) != 0 }

    @Test
    fun everyRecommendedItemHasAnImage() {
        val missing = AgeGroup.entries.flatMap { age ->
            val items = FeelsBand.entries.flatMap { ClothingRecommender.itemsFor(age, it) } +
                ClothingItem.entries.filter { it.conditional }
            items.distinct().filterNot { hasImage(it, age) }.map { "$age/${it.key}" }
        }
        assertTrue("이미지 없음: $missing", missing.isEmpty())
    }
}
