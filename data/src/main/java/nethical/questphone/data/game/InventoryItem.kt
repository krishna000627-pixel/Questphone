package nethical.questphone.data.game

import kotlinx.serialization.Serializable
import nethical.questphone.data.R

@Serializable
enum class Availability(val displayName: String, val rarityValue: Int) {
    COMMON("Common", 1),
    UNCOMMON("Uncommon", 2),
    RARE("Rare", 3),
    EPIC("Epic", 4),
    LEGENDARY("Legendary", 5),
    LIMITED_TIME("Limited Time", 6)
}

@Serializable
enum class StoreCategory(val simpleName: String){
    TOOLS("Tools"),
    BOOSTERS("Boosters"),
    THEMES("Themes"),
    HOME_WIDGET("Home Widget")
}

@Serializable
enum class InventoryItem(val simpleName: String, val description: String, val icon: Int, val isDirectlyUsableFromInventory : Boolean = false, val availability: Availability = Availability.UNCOMMON, val price: Int = 0, val storeCategory: StoreCategory = StoreCategory.TOOLS) {
    STREAK_FREEZER("Streak Freezer", description = "Automatically freezes your streak in case you fail to complete all quests on a day", icon = R.drawable.streak_freezer, price = 20),
    QUEST_SKIPPER("Quest Skipper", description = "Mark any quest as complete", icon = R.drawable.quest_skipper, price = 5),
    QUEST_EDITOR("Quest Editor", description = "Edit information about a quest", icon = R.drawable.quest_editor, price = 20),
    QUEST_DELETER ("Quest Deleter", description = "Destroy a quest.", icon = R.drawable.quest_deletor, price = 100),
    XP_BOOSTER ("XP Booster", description = "Get 2x more xp for the next 5 hours.", isDirectlyUsableFromInventory = true, icon = R.drawable.xp_booster, storeCategory = StoreCategory.BOOSTERS, price = 10),
    DISTRACTION_ADDER("Distraction Adder", description = "Add an app to the distraction list", isDirectlyUsableFromInventory = true,icon = R.drawable.distraction_adder, price = 2),
    DISTRACTION_REMOVER("Distraction Remover", description = "Remove an app from the distractions list", isDirectlyUsableFromInventory = true ,icon = R.drawable.distraction_remover, price = 20),
    REWARD_TIME_EDITOR("Time Editor", description = "Edit how many minutes of screentime you can buy with 1 coin", isDirectlyUsableFromInventory = true, icon = R.drawable.screentime_rewarder, price = 50),
    STREAK_SAVER("Streak Saver", description = "Use to save your streak if you failed today.", isDirectlyUsableFromInventory = true, icon = R.drawable.streak_freezer, price = 100),
    FULL_FREE_DAY("Full Free Day", description = "Enjoy a full day without any app blocks.", isDirectlyUsableFromInventory = true, icon = R.drawable.quest_manage, price = 500),
    STUDY_APPS_MANAGER("Study Apps", description = "Manage your study apps — apps you want to track for focused time.", isDirectlyUsableFromInventory = true, icon = R.drawable.quest_analytics, storeCategory = StoreCategory.TOOLS, price = 0),
    COIN_MULTIPLIER("Coin Multiplier", description = "Earn 2x coins from all quests for the next 24 hours.", isDirectlyUsableFromInventory = true, icon = R.drawable.xp_booster, storeCategory = StoreCategory.BOOSTERS, availability = Availability.RARE, price = 150),
    FOCUS_SHIELD("Focus Shield", description = "Block all distracting apps for 2 hours. No coins can unlock them during this time.", isDirectlyUsableFromInventory = true, icon = R.drawable.distraction_remover, storeCategory = StoreCategory.TOOLS, availability = Availability.UNCOMMON, price = 30),
    VAULT_PASS("Vault Pass", description = "Temporarily disable the Calculator Vault for 1 hour without entering the secret code.", isDirectlyUsableFromInventory = true, icon = R.drawable.quest_manage, storeCategory = StoreCategory.TOOLS, availability = Availability.UNCOMMON, price = 40),
    DAILY_QUEST_REROLL("Quest Reroll", description = "Replace today's hardest quest with a new one.", isDirectlyUsableFromInventory = true, icon = R.drawable.quest_skipper, storeCategory = StoreCategory.TOOLS, availability = Availability.COMMON, price = 25),
    STUDY_BOOST("Study Boost", description = "Your next study session counts as 1.5x hours toward your daily quota.", isDirectlyUsableFromInventory = true, icon = R.drawable.xp_booster, storeCategory = StoreCategory.BOOSTERS, availability = Availability.UNCOMMON, price = 60),

}

