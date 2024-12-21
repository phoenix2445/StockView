package com.example.stockview

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface StockApiService {
    @GET("quote")
    fun getStockPrice(
        @Query("symbol") symbol: String,
        @Query("token") apiKey: String
    ): Call<StockResponse>
}
