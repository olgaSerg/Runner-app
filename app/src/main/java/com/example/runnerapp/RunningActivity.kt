package com.example.runnerapp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isInvisible
import com.example.runnerapp.models.TrackModel
import com.example.runnerapp.providers.RecordTrackProvider
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.LatLng
import java.util.Date
import kotlin.math.roundToInt

const val UPDATE_INTERVAL = (10 * 1000).toLong()
const val FASTEST_INTERVAL: Long = 2000

class RunningActivity : AppCompatActivity() {

    private var textViewTimer: TextView? = null
    private var buttonStart: Button? = null
    private var buttonFinish: Button? = null
    private var timerStarted = false
    private var time = 0.0
    private var serviceIntent: Intent? = null
    private var startTime: Date? = null
    private var routeList = arrayListOf<LatLng>()
    private var totalDistance = 0.0
    private var mLocationRequest: LocationRequest? = null
    private var mFusedLocationClient: FusedLocationProviderClient? = null

    private var mLocationCallback: LocationCallback = object : LocationCallback() {

        override fun onLocationResult(locationResult: LocationResult) {
            val locationList = locationResult.locations
            if (locationList.isNotEmpty()) {
                val location = locationList.last()
                if (routeList.isEmpty()) {
                    val newLocation = LatLng(location.latitude, location.longitude)
                    routeList.add(newLocation)
                }
                if (routeList.isNotEmpty()) {
                    val lastLocation = routeList[routeList.lastIndex]
                    val newLocation = LatLng(location.latitude, location.longitude)
                    if (lastLocation != newLocation) {
                        val result: FloatArray = floatArrayOf(0.0F)
                        Location.distanceBetween(
                            lastLocation.latitude,
                            lastLocation.longitude,
                            newLocation.latitude,
                            newLocation.longitude,
                            result
                        )
                        if (result[0] >= 5) {
                            routeList.add(newLocation)
                            totalDistance += result[0]
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_running)

        textViewTimer = findViewById(R.id.text_view_timer)
        buttonStart = findViewById(R.id.button_start)
        buttonFinish = findViewById(R.id.button_finish)

        val buttonFinish = buttonFinish ?: return
        val db = App.instance?.dBHelper?.writableDatabase ?: return

        setButtonStartListener(buttonFinish)
        setButtonFinishListener(buttonFinish, db)

        serviceIntent = Intent(applicationContext, TimerService::class.java)
        registerReceiver(updateTime, IntentFilter(TimerService.TIMER_UPDATED))
    }

    @SuppressLint("MissingPermission")
    private fun setButtonStartListener(buttonFinish: Button) {
        val buttonStart = buttonStart ?: return
        buttonStart.setOnClickListener {
            buttonStart.isInvisible = true
            buttonFinish.isInvisible = false
            startTime = Date()
            mLocationRequest = LocationRequest.create()
            if (mLocationRequest != null) {
                mLocationRequest!!.interval = UPDATE_INTERVAL
                mLocationRequest!!.fastestInterval = FASTEST_INTERVAL
                mLocationRequest!!.priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
                mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
                mFusedLocationClient?.requestLocationUpdates(
                    mLocationRequest!!,
                    mLocationCallback,
                    Looper.myLooper()!!
                )
                startTimer()
            }
        }
    }

    private fun startTimer() {
        serviceIntent?.putExtra(TimerService.TIME_EXTRA, time)
        startService(serviceIntent)
        timerStarted = true
    }

    private fun setButtonFinishListener(buttonFinish: Button, db: SQLiteDatabase) {
        buttonFinish.setOnClickListener {
            mFusedLocationClient?.removeLocationUpdates(mLocationCallback)
            stopTimer()
            buttonFinish.isInvisible = true
            val track = TrackModel()
            track.duration = time.toLong() / 1000
            track.startTime = startTime
            track.routeList = routeList
            track.distance = totalDistance.toInt()
            val recordTrackProvider = RecordTrackProvider()
            recordTrackProvider.recordTrackExecute(db, track)
        }
    }

    private fun stopTimer() {
        stopService(serviceIntent)
        timerStarted = false
    }

    private val updateTime: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val textViewTimer = textViewTimer ?: return
            val currentTime = Date()
            if (startTime != null) {
                time = 1.0 * (currentTime.time - startTime!!.time)
                textViewTimer.text = getTimeStringFromDouble(time)
            }
        }
    }

    private fun getTimeStringFromDouble(time: Double): String {
        val resultInt = time.roundToInt() / 1000
        val hours = resultInt % 86400 / 3600
        val minutes = resultInt % 86400 % 3600 / 60
        val seconds = resultInt % 86400 % 3600 % 60
        val millis = time.roundToInt() % 1000

        return makeTimeString(hours, minutes, seconds, millis)
    }

    private fun makeTimeString(hour: Int, min: Int, sec: Int, millis: Int): String =
        String.format("%02d:%02d:%02d.%03d", hour, min, sec, millis)

    override fun onBackPressed() {
        if (timerStarted) {
            Toast.makeText(applicationContext, "Нажмите кнопку \"Финиш\"", Toast.LENGTH_SHORT)
                .show()
        } else {
            super.onBackPressed()
        }
    }
}


