package com.zozi.feels_likeweather.domain

import java.util.UUID

enum class AgeGroup(val label: String, val description: String) {
    INFANT("유아", "영유아·미취학"),
    CHILD("아동", "초등학생"),
    ADULT("성인", "청소년·성인"),
    SENIOR("중장년", "50대 이상"),
}

/**
 * 옷차림 추천 프로필.
 *
 * @param activityStartHour 활동 시작 시각 (0..23)
 * @param activityEndHour 활동 종료 시각 (1..24, 시작보다 큼)
 * @param isMine '내 기준' 프로필 여부. 삭제할 수 없고, 내 체감 구간(설문 보정)을 사용한다.
 */
data class Profile(
    val id: String,
    val name: String,
    val ageGroup: AgeGroup,
    val activityStartHour: Int,
    val activityEndHour: Int,
    val isMine: Boolean = false,
) {
    companion object {
        const val MINE_ID = "mine"

        fun new(): Profile = Profile(UUID.randomUUID().toString(), "", AgeGroup.ADULT, 9, 18)

        val defaults: List<Profile> = listOf(
            Profile(MINE_ID, "내 기준", AgeGroup.ADULT, 8, 20, isMine = true),
            Profile("default-infant", "유아", AgeGroup.INFANT, 9, 17),
            Profile("default-child", "아동", AgeGroup.CHILD, 8, 17),
            Profile("default-adult", "성인", AgeGroup.ADULT, 9, 18),
            Profile("default-senior", "중장년", AgeGroup.SENIOR, 7, 19),
        )
    }
}
