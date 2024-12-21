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

class MainMenuAuthActivity : AppCompatActivity() {

    private lateinit var stockApi: StockApiService
    private val apiKey = "cte2jb9r01qt478ku970cte2jb9r01qt478ku97g" // Ganti dengan API Key Anda dari Finnhub

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mainmenu_auth)

        val etStockSymbol: EditText = findViewById(R.id.et_stock_symbol)
        val btnGetPrice: Button = findViewById(R.id.btn_get_price)
        val tvStockPrice: TextView = findViewById(R.id.tv_stock_price)
        val lineChart: LineChart = findViewById(R.id.line_chart)
        val progressBar: ProgressBar = findViewById(R.id.progress_bar)
        val btnLogout: Button = findViewById(R.id.btn_logout)

        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, MainMenuActivity::class.java))
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
        // Pantau pembaruan data di Firebase
        listenForStockUpdates()
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
                        tvStockPrice.text = "Stock Price: $%.2f".format(currentPrice)

                        // Mendapatkan UID pengguna
                        val currentUser = FirebaseAuth.getInstance().currentUser
                        if (currentUser != null) {
                            val userId = currentUser.uid

                            // Firebase reference untuk menyimpan data berdasarkan UID pengguna dan simbol saham
                            val database = FirebaseDatabase.getInstance("https://myfirebase-5e286-default-rtdb.asia-southeast1.firebasedatabase.app/").reference
                            val stockRef = database.child("users").child(userId).child("stocks").child(symbol)

                            stockRef.get().addOnSuccessListener { snapshot ->
                                val previousData = snapshot.getValue(StockData::class.java)

                                // Cek apakah data sebelumnya ada atau tidak
                                if (previousData != null) {
                                    val previousPrice = previousData.price
                                    val difference = currentPrice - previousPrice
                                    val percentageChange = (difference / previousPrice) * 100

                                    // Update UI dengan harga sebelumnya dan perbedaan
                                    val tvPreviousPrice: TextView = findViewById(R.id.tv_previous_price)
                                    val tvPriceDifference: TextView = findViewById(R.id.tv_price_difference)
                                    val tvPreviousDate: TextView = findViewById(R.id.tv_previous_date)

                                    tvPreviousPrice.text = "Previous Price: $%.2f".format(previousPrice)
                                    tvPriceDifference.text = "Difference: $%.2f (%.2f%%)".format(difference, percentageChange)
                                    tvPriceDifference.setTextColor(
                                        if (difference > 0) android.graphics.Color.GREEN else android.graphics.Color.RED
                                    )
                                    tvPreviousDate.text = "Previous Date: ${previousData.date}"
                                } else {
                                    // Jika data sebelumnya tidak ada, set ke 0
                                    val tvPreviousPrice: TextView = findViewById(R.id.tv_previous_price)
                                    val tvPriceDifference: TextView = findViewById(R.id.tv_price_difference)
                                    val tvPreviousDate: TextView = findViewById(R.id.tv_previous_date)

                                    tvPreviousPrice.text = "Previous Price: $0.00"
                                    tvPriceDifference.text = "Difference: $0.00 (0%)"
                                    tvPriceDifference.setTextColor(android.graphics.Color.BLACK)
                                    tvPreviousDate.text = "Previous Date: -"
                                }

                                // Simpan data saham terbaru
                                val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                val newData = StockData(symbol, currentPrice, currentDate)

                                // Simpan data saham terbaru pada Firebase
                                stockRef.setValue(newData)

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
                            }
                        } else {
                            tvStockPrice.text = "Failed to fetch data"
                        }
                    }
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
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 40)
            set(Calendar.SECOND, 0)
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, openTime.timeInMillis, openPendingIntent)

        val closeIntent = Intent(this, NotificationReceiver::class.java).apply {
            putExtra("title", "Pasar Ditutup!")
            putExtra("message", "Pasar saham telah ditutup. Jangan lupa cek performa!")
        }
        val closePendingIntent = PendingIntent.getBroadcast(this, 1, closeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val closeTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, closeTime.timeInMillis, closePendingIntent)
    }


    private fun listenForStockUpdates() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            val database = FirebaseDatabase.getInstance("https://myfirebase-5e286-default-rtdb.asia-southeast1.firebasedatabase.app/").reference
            val stockRef = database.child("users").child(userId).child("stocks")

            // Mendengarkan pembaruan pada child stocks
            stockRef.addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    showUpdateNotification(snapshot)
                }
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    showUpdateNotification(snapshot)
                }
                override fun onChildRemoved(snapshot: DataSnapshot) {
                    // Tindakan ketika data saham dihapus
                }
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                    // Tindakan ketika data saham berpindah urutannya
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "Error fetching stock data: ${error.message}")
                }
            })
        }
    }

    private fun showUpdateNotification(snapshot: DataSnapshot) {
        // Mengambil data saham dari snapshot
        val stockData = snapshot.getValue(StockData::class.java)
        if (stockData != null) {
            // Menampilkan notifikasi jika ada pembaruan data saham
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = System.currentTimeMillis().toInt()

            val notificationBuilder = NotificationCompat.Builder(this, "update_channel")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Data Baru!")
                .setContentText("Data saham ${stockData.symbol} diperbarui. Harga saat ini: ${stockData.price}")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            notificationManager.notify(notificationId, notificationBuilder.build())
        }
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

            val updateChannel = NotificationChannel(
                "update_channel",
                "Update Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi untuk pembaruan data saham"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(marketChannel)
            notificationManager.createNotificationChannel(updateChannel)
        }
    }
}