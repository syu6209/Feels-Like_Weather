package com.zozi.feels_likeweather.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 받아온 날씨를 지역별로 보관하는 로컬 DB.
 * - location: 지역 1행(이름·좌표·받은 시각·받을 때의 현재 날씨 now_*)
 * - hourly / daily: 지역별 시간별·일별 예보 (어제 ~ 오늘 포함 7일)
 */
class WeatherDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE location (
                key TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                utc_offset INTEGER NOT NULL,
                fetched_at INTEGER NOT NULL,
                viewed_at INTEGER NOT NULL DEFAULT 0,
                now_time TEXT NOT NULL,
                now_temperature REAL NOT NULL,
                now_apparent REAL NOT NULL,
                now_humidity INTEGER NOT NULL,
                now_code INTEGER NOT NULL,
                now_wind REAL NOT NULL,
                now_precipitation REAL NOT NULL,
                now_is_day INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE hourly (
                location_key TEXT NOT NULL REFERENCES location(key) ON DELETE CASCADE,
                time TEXT NOT NULL,
                temperature REAL NOT NULL,
                apparent REAL NOT NULL,
                humidity INTEGER NOT NULL,
                precipitation_probability INTEGER NOT NULL,
                precipitation REAL NOT NULL,
                code INTEGER NOT NULL,
                wind REAL NOT NULL,
                uv REAL NOT NULL,
                is_day INTEGER NOT NULL,
                PRIMARY KEY (location_key, time)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE daily (
                location_key TEXT NOT NULL REFERENCES location(key) ON DELETE CASCADE,
                date TEXT NOT NULL,
                code INTEGER NOT NULL,
                temperature_max REAL NOT NULL,
                temperature_min REAL NOT NULL,
                apparent_max REAL NOT NULL,
                apparent_min REAL NOT NULL,
                precipitation_probability_max INTEGER NOT NULL,
                precipitation_sum REAL NOT NULL,
                uv_max REAL NOT NULL,
                sunrise TEXT,
                sunset TEXT,
                PRIMARY KEY (location_key, date)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 캐시일 뿐이므로 구조가 바뀌면 지우고 다시 받는다.
        db.execSQL("DROP TABLE IF EXISTS hourly")
        db.execSQL("DROP TABLE IF EXISTS daily")
        db.execSQL("DROP TABLE IF EXISTS location")
        onCreate(db)
    }

    companion object {
        private const val NAME = "weather.db"
        private const val VERSION = 1

        @Volatile private var instance: WeatherDatabase? = null

        /** 앱과 위젯·백그라운드 작업이 같은 연결을 쓰도록 하나만 만든다. */
        fun get(context: Context): WeatherDatabase =
            instance ?: synchronized(this) { instance ?: WeatherDatabase(context).also { instance = it } }
    }
}
