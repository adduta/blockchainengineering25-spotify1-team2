package nl.tudelft.trustchain.musicdao.core.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ListenCounter {
    private const val PREFS_NAME = "listen_counter_prefs"
    private const val COUNTS_KEY = "artist_listen_counts"
    private var listenCounts: MutableMap<String, Int> = mutableMapOf()
    private var initialized = false

    fun initialize(context: Context) {
        if (!initialized) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(COUNTS_KEY, null)
            if (json != null) {
                val type = object : TypeToken<MutableMap<String, Int>>() {}.type
                listenCounts = Gson().fromJson(json, type) ?: mutableMapOf()
            }
            initialized = true
        }
    }

    fun increment(context: Context, artistPublicKey: String) {
        initialize(context)
        listenCounts[artistPublicKey] = (listenCounts[artistPublicKey] ?: 0) + 1
        save(context)
    }

    fun getCount(context: Context, artistPublicKey: String): Int {
        initialize(context)
        return listenCounts[artistPublicKey] ?: 0
    }

    fun getAllCounts(context: Context): Map<String, Int> {
        initialize(context)
        return listenCounts.toMap()
    }

    private fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(COUNTS_KEY, Gson().toJson(listenCounts)).apply()
    }
} 