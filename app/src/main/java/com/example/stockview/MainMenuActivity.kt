package com.example.stockview

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class MainMenuActivity : AppCompatActivity() {

    private lateinit var stockApi: StockApiService
    private val apiKey = "cte2jb9r01qt478ku970cte2jb9r01qt478ku97g" // Ganti dengan API Key Anda dari Finnhub

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mainmenu)

        val etStockSymbol: EditText = findViewById(R.id.et_stock_symbol)
        val btnGetPrice: Button = findViewById(R.id.btn_get_price)
        val tvStockPrice: TextView = findViewById(R.id.tv_stock_price)
        val lineChart: LineChart = findViewById(R.id.line_chart)
        val progressBar: ProgressBar = findViewById(R.id.progress_bar)
        val btnLogout: Button = findViewById(R.id.btn_logout)

        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Inisialisasi Retrofit
        stockApi = ApiClient.instance.create(StockApiService::class.java)

        btnGetPrice.setOnClickListener {
            val symbol = etStockSymbol.text.toString().uppercase()
            if (symbol.isNotEmpty()) {
                fetchStockPrice(symbol, tvStockPrice, lineChart, progressBar)
            } else {
                Toast.makeText(this, "Please enter a stock symbol!", Toast.LENGTH_SHORT).show()
            }
        }
        // Membuat channel notifikasi
        createNotificationChannels()
        // Jadwalkan notifikasi waktu pasar
        scheduleMarketNotifications()
    }

    private fun fetchStockPrice(symbol: String, tvStockPrice: TextView, lineChart: LineChart, progressBar: ProgressBar) {
        progressBar.visibility = View.VISIBLE

        val call = stockApi.getStockPrice(symbol = symbol, apiKey = apiKey)
        call.enqueue(object : Callback<StockResponse> {
            override fun onResponse(call: Call<StockResponse>, response: Response<StockResponse>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful) {
                    val stockData = response.body()
                    if (stockData != null) {
                        val currentPrice = stockData.c

                        if (currentPrice <= 0) {
                            Toast.makeText(this@MainMenuActivity, "Invalid stock price. Please check the symbol.", Toast.LENGTH_SHORT).show()
                            return
                        }
                        tvStockPrice.text = "Stock Price: $%.2f".format(currentPrice)

                        val entries = listOf(
                            Entry(1f, stockData.o.toFloat()),
                            Entry(2f, stockData.h.toFloat()),
                            Entry(3f, stockData.l.toFloat()),
                            Entry(4f, stockData.c.toFloat())
                        )

                        val dataSet = LineDataSet(entries, "Stock Price History")
                        val lineData = LineData(dataSet)
                        lineChart.data = lineData
                        lineChart.invalidate()
                    } else {
                        tvStockPrice.text = "Data not found"
                    }
                } else {
                    tvStockPrice.text = "Failed to fetch data"
                }
            }

            override fun onFailure(call: Call<StockResponse>, t: Throwable) {
                progressBar.visibility = View.GONE
                tvStockPrice.text = "Error: ${t.message}"
            }
        })
    }

    private fun scheduleMarketNotifications() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val openIntent = Intent(this, NotificationReceiver::class.java).apply {
            putExtra("title", "Pasar Dibuka!")
            putExtra("message", "Pasar saham sekarang telah dibuka. Saatnya memantau!")
        }
        val openPendingIntent = PendingIntent.getBroadcast(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val openTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, openTime.timeInMillis, openPendingIntent)

        val closeIntent = Intent(this, NotificationReceiver::class.java).apply {
            putExtra("title", "Pasar Ditutup!")
            putExtra("message", "Pasar saham telah ditutup. Jangan lupa cek performa!")
        }
        val closePendingIntent = PendingIntent.getBroadcast(this, 1, closeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val closeTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, closeTime.timeInMillis, closePendingIntent)
    }


    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val marketChannel = NotificationChannel(
                "market_channel",
                "Market Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi untuk waktu pasar"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(marketChannel)
        }
    }
}