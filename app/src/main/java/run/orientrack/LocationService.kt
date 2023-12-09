package run.orientrack

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.cert.CertificateException
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class LocationService: Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationClient: LocationClient
    private val client = getUnsafeOkHttpClient()
    private var displayName = ""

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        locationClient = DefaultLocationClient(
            applicationContext,
            LocationServices.getFusedLocationProviderClient(applicationContext)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_START -> {
                displayName = intent.getStringExtra("displayName").toString()
                start()}
            ACTION_STOP -> stop()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @SuppressLint("HardwareIds")
    private fun start() {
        val notification = NotificationCompat.Builder(this, "location")
            .setContentTitle("Tracking location...")
            .setContentText("Location: null")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        locationClient
            .getLocationUpdates(100L)
            .catch { e -> e.printStackTrace() }
            .onEach { location ->
                var batteryLevel = 0;
                val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                    this.registerReceiver(null, ifilter)
                }

                batteryStatus?.let { intent ->
                    val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    batteryLevel = level;
                }

                val androidId= Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)
                val lat = location.latitude.toString()
                val long = location.longitude.toString()
                val accuracy = location.accuracy.toString()
                val speed = location.speed.toString()
                val bearing = location.bearing.toString()
                val altitude = location.altitude.toString()
                val time = location.time.toString()
                val battery = batteryLevel.toString()

                val updatedNotification = notification.setContentText(
                    "Location: ($lat, $long), Name: $displayName"
                )
                notificationManager.notify(1, updatedNotification.build())

                val requestBodyJSON = """
                    {
                        "id": "$androidId",
                        "display_name": "$displayName",
                        "lat": $lat,
                        "lon": $long,
                        "accuracy": $accuracy,
                        "speed": $speed,
                        "bearing": $bearing,
                        "altitude": $altitude,
                        "time": $time,
                        "battery": $battery
                    }
                """.trimIndent()

                val json = "application/json; charset=utf-8".toMediaType()
                val request = Request.Builder()
                    .url(getString(R.string.https_backend_orientrack_run_track))
                    .post(requestBodyJSON.toRequestBody(json))
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
//                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                        var num = 42;
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                }
            .launchIn(serviceScope)

        startForeground(1, notification.build())
    }
    private fun getUnsafeOkHttpClient(): OkHttpClient {
        return try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<TrustManager>(
                @SuppressLint("CustomX509TrustManager")
                object : X509TrustManager {
                    @SuppressLint("TrustAllX509TrustManager")
                    @Throws(CertificateException::class)
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                    }

                    @SuppressLint("TrustAllX509TrustManager")
                    @Throws(CertificateException::class)
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                    }

                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                        return arrayOf()
                    }
                }
            )

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            // Create an ssl socket factory with our all-trusting manager
            val sslSocketFactory = sslContext.socketFactory

            val builder = OkHttpClient.Builder()
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }

            builder.build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun stop() {
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }
}