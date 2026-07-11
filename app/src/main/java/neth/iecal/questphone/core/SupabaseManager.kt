package neth.iecal.questphone.core

import android.content.Context
import android.util.Log

/**
 * Supabase is disabled — login/sync removed.
 * This stub prevents any crash from leftover import references.
 */
object Supabase {
    val url: String = ""
    val apiKey: String = ""

    fun initialize(context: Context) {
        // no-op
    }

    suspend fun awaitSession(): String? {
        return null
    }
}
