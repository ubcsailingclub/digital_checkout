package com.ubcsc.checkout.data.api

import com.ubcsc.checkout.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        // Attach kiosk API key to every request
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("X-Kiosk-Key", BuildConfig.KIOSK_API_KEY)
                .build()
            chain.proceed(request)
        }
        // Log requests/responses in debug builds
        .apply {
            if (BuildConfig.DEBUG) {
                val logging = HttpLoggingInterceptor()
                    .also { it.level = HttpLoggingInterceptor.Level.BODY }
                addInterceptor(logging)
            }
        }
        .build()

    val api: CheckoutApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CheckoutApi::class.java)
}
