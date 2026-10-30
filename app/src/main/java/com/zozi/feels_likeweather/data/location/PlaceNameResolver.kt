package com.zozi.feels_likeweather.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.zozi.feels_likeweather.data.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/** 안드로이드 내장 [Geocoder] 를 이용한 좌표 ↔ 지역명 변환. */
class PlaceNameResolver(context: Context) {

    private val geocoder = Geocoder(context, Locale.KOREA)

    /**
     * 좌표의 지역명을 "지역명(도시명)" 형태로 돌려준다. 예) "분당구(성남시)", "강남구(서울특별시)".
     * 구/동 정보가 없으면 도시명만 돌려준다.
     */
    suspend fun displayName(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val address = fromLocation(latitude, longitude) ?: return null
        return formatAddress(address)
    }

    suspend fun search(query: String): List<Place> {
        if (!Geocoder.isPresent()) return emptyList()
        val addresses = fromName(query)
        return addresses
            .filter { it.hasLatitude() && it.hasLongitude() }
            .map { a ->
                Place(
                    name = formatAddress(a) ?: query,
                    region = listOfNotNull(a.adminArea, a.countryName).distinct().joinToString(", "),
                    latitude = a.latitude,
                    longitude = a.longitude,
                )
            }
            .distinctBy { it.name }
    }

    private fun formatAddress(a: Address): String? {
        val city = a.locality ?: a.adminArea
        val district = a.subLocality ?: a.thoroughfare ?: a.subAdminArea
        return when {
            city != null && district != null && district != city -> "$district($city)"
            city != null -> city
            district != null -> district
            else -> a.featureName
        }
    }

    private suspend fun fromLocation(lat: Double, lon: Double): Address? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (cont.isActive) cont.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
            }
        }

    private suspend fun fromName(query: String): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(query, 10, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (cont.isActive) cont.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resume(emptyList())
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocationName(query, 10).orEmpty() }.getOrDefault(emptyList())
            }
        }
}
