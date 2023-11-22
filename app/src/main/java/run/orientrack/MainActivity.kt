package run.orientrack

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.ncorti.slidetoact.SlideToActView

class MainActivity : AppCompatActivity() {
    // in the below line, we are creating variables.

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
                0
            )
        }
       else {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
                0
            )
        }

        setContentView(R.layout.activity_main)

        val idTextView = findViewById<TextView>(R.id.idTextView)
        idTextView.text = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val startSlider = findViewById<SlideToActView>(R.id.start)
        val stopSlider = findViewById<SlideToActView>(R.id.stop)

        startSlider.onSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
            override fun onSlideComplete(view: SlideToActView) {
                stopSlider.setCompleted(completed = false, withAnimation = true)
                Intent(applicationContext, LocationService::class.java).apply {
                    action = LocationService.ACTION_START
                    startService(this)
                }
            }
        }

        stopSlider.onSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
            override fun onSlideComplete(view: SlideToActView) {
                startSlider.setCompleted(completed = false, withAnimation = true)
                Intent(applicationContext, LocationService::class.java).apply {
                    action = LocationService.ACTION_STOP
                    startService(this)
                }
            }
        }
    }
}