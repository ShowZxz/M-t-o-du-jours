package com.example.lameteodujours

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Build.VERSION_CODES.O
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.example.lameteodujours.DataCenter.ModelBase
import com.example.lameteodujours.Outils.ApiOutil
import com.example.lameteodujours.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationProviderClient:FusedLocationProviderClient
    private lateinit var activityMainBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        activityMainBinding = DataBindingUtil.setContentView(this,R.layout.activity_main)
        supportActionBar?.hide()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        activityMainBinding.rlMainLayout.visibility = View.GONE

        getCurrentLocation()

        activityMainBinding.etGetCityName.setOnEditorActionListener({v, actionId, keyEvent->
            if (actionId== EditorInfo.IME_ACTION_SEARCH)
            {
                getCityWeather(activityMainBinding.etGetCityName.text.toString())
                val view=this.currentFocus
                if (view!=null){
                    val imm: InputMethodManager =getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken,0)
                    activityMainBinding.etGetCityName.clearFocus()
                }
                true
            }
            else false
        })


    }



    private fun getCityWeather(cityName: String) {

        activityMainBinding.pbLoading.visibility=View.VISIBLE
        ApiOutil.getApiInterface()?.getCityWeatherData(cityName, API_KEY )?.enqueue(object :Callback<ModelBase>
        {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onResponse(call: Call<ModelBase>, response: Response<ModelBase>) {
                setDataOnViews(response.body())
            }

            override fun onFailure(call: Call<ModelBase>, t: Throwable) {
                Toast.makeText(applicationContext, "Le nom de la ville est pas valide",Toast.LENGTH_SHORT).show()
            }

        })

    }


    private fun getCurrentLocation()
    {
        if (checkPermissions())
        {
            if (isLocationEnabled())
            {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                {
                    requestPermissions()
                    return
                }
                fusedLocationProviderClient.lastLocation.addOnCompleteListener(this) { task->
                    val location: Location?=task.result
                    if (location==null)
                    {
                        Toast.makeText(this,"Null Received", Toast.LENGTH_SHORT).show()

                    }
                    else
                    {
                        fetchCurrentLocationWeather(location.latitude.toString(),location.longitude.toString())
                    }
                }

            }
            else
            {
                Toast.makeText(this,"Veuillez activer la localisation", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        } else {
            requestPermissions()
        }



    }

    private fun fetchCurrentLocationWeather(latitude: String, longitude: String) {

        activityMainBinding.pbLoading.visibility= View.VISIBLE
        ApiOutil.getApiInterface()?.getCurrentWeatherData(latitude,longitude,API_KEY)?.enqueue(object :
            Callback<ModelBase> {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onResponse(call: Call<ModelBase>, response: Response<ModelBase>) {
                if(response.isSuccessful)
                {

                    setDataOnViews(response.body())
                }

            }

            override fun onFailure(call: Call<ModelBase>, t: Throwable) {
                Toast.makeText(applicationContext, t.message,Toast.LENGTH_SHORT).show()
                println(t.stackTraceToString())
            }



        })


    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun setDataOnViews(body: ModelBase?) {
        val sdf= SimpleDateFormat("dd/MM/yyyy hh:mm")
        val currentDate=sdf.format(Date())
        activityMainBinding.tvDateTime.text=currentDate

        activityMainBinding.tvDayMaxTemp.text="Journée "+kelvinToCelcsius(body!!.main.temp)+"°"
        activityMainBinding.tvDayMinTemp.text="nuit " + kelvinToCelcsius(body!!.main.temp_min) +"°"
        activityMainBinding.tvTemp.text=""+kelvinToCelcsius(body!!.main.temp)+"°"
        activityMainBinding.tvFeelsLike.text="Ressenti " + kelvinToCelcsius(body!!.main.feels_like) +"°"
        activityMainBinding.tvWeatherType.text=body.weather[0].main
        activityMainBinding.tvSunrise.text=timeStampToLocalDate(body.sys.sunrise.toLong())
        activityMainBinding.tvSunset.text=timeStampToLocalDate(body.sys.sunset.toLong())
        activityMainBinding.tvPressure.text=body.main.pressure.toString()
        activityMainBinding.tvHumidity.text=body.main.humidity.toString()+" %"
        activityMainBinding.tvWindSpeed.text=body.wind.speed.toString()+" M/S"
        activityMainBinding.tvTempFarenhite.text = "" +((kelvinToCelcsius(body.main.temp)).times(1.8).plus(32))

        activityMainBinding.etGetCityName.setText(body.name)

        updateUi(body.weather[0].id)



    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun timeStampToLocalDate(timeStamp: Long): String {
        val localTime= timeStamp.let {
            Instant.ofEpochSecond(it)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
        }
        return localTime.toString()
    }

    private fun kelvinToCelcsius(temp: Double): Double {
        var intTemp = temp
        intTemp= intTemp.minus(273)
        return intTemp.toBigDecimal().setScale(1, RoundingMode.UP).toDouble()

    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION,android.Manifest.permission.ACCESS_FINE_LOCATION
        ),
            PERMISSION_REQUEST_ACCESS_LOCATION
        )


    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)||locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER)


    }

    private fun checkPermissions():Boolean {
        if(ActivityCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)

        {
            return true
        }
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == PERMISSION_REQUEST_ACCESS_LOCATION){
            println("------------------------ OKKKKKKKKKKK")
            if (grantResults.isNotEmpty()&& grantResults[O]==PackageManager.PERMISSION_GRANTED){
                Toast.makeText(applicationContext,"Granted",Toast.LENGTH_SHORT).show()
                getCurrentLocation()
            } else{
                Toast.makeText(applicationContext,"Denied",Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object{
        private const val PERMISSION_REQUEST_ACCESS_LOCATION=100
        const val API_KEY ="866305780ede17150faaf227a39c1c78"
    }




    private fun updateUi(id: Int) {
        if (id in 200..232){
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = resources.getColor(R.color.thunderstorm)
            activityMainBinding.rlToolbar.setBackgroundColor(resources.getColor(R.color.thunderstorm))
            activityMainBinding.rlSubLayout.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.thunderbolt
            )
            activityMainBinding.llMainBgBelow.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.thunderbolt
            )
            activityMainBinding.llMainBgAbove.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.thunderbolt

            )
            activityMainBinding.ivWeatherBg.setImageResource(R.drawable.thunderbolt)
            activityMainBinding.ivWeatherIcon.setImageResource(R.drawable.storm)

        }  else if (id in 300..321){

            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = resources.getColor(R.color.drizzle)
            activityMainBinding.rlToolbar.setBackgroundColor(resources.getColor(R.color.drizzle))
            activityMainBinding.rlSubLayout.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.drizzle_bg
            )
            activityMainBinding.llMainBgBelow.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.drizzle_bg
            )
            activityMainBinding.llMainBgAbove.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.drizzle_bg

            )
            activityMainBinding.ivWeatherBg.setImageResource(R.drawable.drizzle_bg)
            activityMainBinding.ivWeatherIcon.setImageResource(R.drawable.drizzle)

        }
        else if (id in 500..531){

            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = resources.getColor(R.color.rain)
            activityMainBinding.rlToolbar.setBackgroundColor(resources.getColor(R.color.rain))
            activityMainBinding.rlSubLayout.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.rainy_bg
            )
            activityMainBinding.llMainBgBelow.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.rainy_bg
            )
            activityMainBinding.llMainBgAbove.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.rainy_bg

            )
            activityMainBinding.ivWeatherBg.setImageResource(R.drawable.rainy_bg)
            activityMainBinding.ivWeatherIcon.setImageResource(R.drawable.rainy_day)


        }
        else if (id in 600..620){
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = resources.getColor(R.color.rain)
            activityMainBinding.rlToolbar.setBackgroundColor(resources.getColor(R.color.snow))
            activityMainBinding.rlSubLayout.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.snow_bg
            )
            activityMainBinding.llMainBgBelow.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.snow_bg
            )
            activityMainBinding.llMainBgAbove.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.snow_bg

            )
            activityMainBinding.ivWeatherBg.setImageResource(R.drawable.snow_bg)
            activityMainBinding.ivWeatherIcon.setImageResource(R.drawable.snowflake)

        }
        else if (id in 701..781){
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = resources.getColor(R.color.atmosphere)
            activityMainBinding.rlToolbar.setBackgroundColor(resources.getColor(R.color.atmosphere))
            activityMainBinding.rlSubLayout.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.mist_bg
            )
            activityMainBinding.llMainBgBelow.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.mist_bg
            )
            activityMainBinding.llMainBgAbove.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.mist_bg

            )
            activityMainBinding.ivWeatherBg.setImageResource(R.drawable.mist_bg)
            activityMainBinding.ivWeatherIcon.setImageResource(R.drawable.mist)
        }
        else if (id==800){
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = resources.getColor(R.color.clear)
            activityMainBinding.rlToolbar.setBackgroundColor(resources.getColor(R.color.clear))
            activityMainBinding.rlSubLayout.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.clear_bg
            )
            activityMainBinding.llMainBgBelow.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.clear_bg
            )
            activityMainBinding.llMainBgAbove.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.clear_bg

            )
            activityMainBinding.ivWeatherBg.setImageResource(R.drawable.clear_bg)
            activityMainBinding.ivWeatherIcon.setImageResource(R.drawable.sun)

        }
        else{
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = resources.getColor(R.color.clouds)
            activityMainBinding.rlToolbar.setBackgroundColor(resources.getColor(R.color.clouds))
            activityMainBinding.rlSubLayout.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.clouds
            )
            activityMainBinding.llMainBgBelow.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.clouds_bg
            )
            activityMainBinding.llMainBgAbove.background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.clouds_bg

            )
            activityMainBinding.ivWeatherBg.setImageResource(R.drawable.clouds_bg)
            activityMainBinding.ivWeatherIcon.setImageResource(R.drawable.clouds)
        }
        activityMainBinding.pbLoading.visibility = View.GONE
        activityMainBinding.rlMainLayout.visibility = View.VISIBLE

    }

}