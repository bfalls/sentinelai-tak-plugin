package com.sentinelai.tak.plugin.location

import android.util.Log
import com.atakmap.android.location.framework.LocationManager
import com.atakmap.coremap.maps.coords.GeoPoint

/**
 * Lightweight adapter around CivTAK's location framework that exposes the
 * preferred ownship location without touching Android's device GPS APIs.
 */
data class CivTakLocation(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val horizontalSource: String? = null,
    val verticalSource: String? = null,
)

interface OwnshipLocationProvider {
    fun getCurrentLocation(): CivTakLocation?
}

class CivTakLocationProvider(
    private val locationManager: LocationManager = LocationManager.getInstance(),
) : OwnshipLocationProvider {

    override fun getCurrentLocation(): CivTakLocation? {
        return try {
            val provider = locationManager.preferredLocationProvider
                ?: return null.also { Log.d(TAG, "No preferred CivTAK location provider available") }

            val fix = provider.lastReportedLocation
                ?: return null.also { Log.d(TAG, "Preferred CivTAK provider has not reported a location yet") }

            if (!fix.isValid) {
                Log.w(TAG, "Ignoring invalid CivTAK location fix from ${provider.uniqueIdentifier}")
                return null
            }

            val point = fix.point
            val altitude = point.altitude
            val altitudeMeters = if (GeoPoint.isAltitudeValid(altitude)) altitude else null
            val derivation = fix.derivation

            CivTakLocation(
                latitude = point.latitude,
                longitude = point.longitude,
                altitudeMeters = altitudeMeters,
                horizontalSource = derivation?.horizontalSource,
                verticalSource = derivation?.verticalSource,
            ).also {
                Log.d(
                    TAG,
                    "CivTAK ownship fix lat=${it.latitude}, lon=${it.longitude}, alt=${it.altitudeMeters}, " +
                        "hSrc=${it.horizontalSource}, vSrc=${it.verticalSource}"
                )
            }
        } catch (ex: Throwable) {
            Log.w(TAG, "Unable to read CivTAK location", ex)
            null
        }
    }

    private val com.atakmap.android.location.framework.Location.isValid: Boolean
        get() = try {
            this.isValid()
        } catch (ex: Throwable) {
            false
        }

    companion object {
        private const val TAG = "CivTakLocationProvider"
    }
}
