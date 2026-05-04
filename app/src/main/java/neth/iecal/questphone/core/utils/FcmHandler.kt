package neth.iecal.questphone.core.utils

import android.content.Context
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.data.game.InventoryItem
import nethical.questphone.data.json

object FcmHandler {
    fun handleData(context: Context, data: Map<String, String>?, userRepository: UserRepository) {
        if (data.isNullOrEmpty()) return
        // Supabase/FCM sync disabled — handle local gifts only
        if (data.containsKey("gifts")) {
            try {
                val items = json.decodeFromString<HashMap<InventoryItem, Int>>(data["gifts"].toString())
                userRepository.addItemsToInventory(items)
            } catch (_: Exception) {}
        }
        if (data.containsKey("gift_coins")) {
            val coins = data["coins"]?.toIntOrNull() ?: 0
            if (coins > 0) userRepository.addCoins(coins)
        }
    }
}
