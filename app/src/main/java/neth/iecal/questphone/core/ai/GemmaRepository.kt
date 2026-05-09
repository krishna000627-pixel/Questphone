package neth.iecal.questphone.core.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import neth.iecal.questphone.BuildConfig
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.focus.isStrangerMode
import neth.iecal.questphone.core.focus.setStrangerMode
import neth.iecal.questphone.data.CommonQuestInfo
import nethical.questphone.core.core.utils.getCurrentDate
import nethical.questphone.data.BaseIntegrationId
import nethical.questphone.data.CustomVoiceAction
import nethical.questphone.data.DayOfWeek
import nethical.questphone.data.game.InventoryItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GemmaRepositoryEntryPoint {
    fun gemmaRepository(): GemmaRepository
}

@kotlinx.serialization.Serializable
data class ChatMessage(val role: String, val text: String)
data class AiResponse(val text: String, val actionDone: String? = null)

// Available AI models
val KAI_MODELS = listOf(
    "gemma-3-27b-it"              to "Gemma 4 27B (Deprecated — switch to Gemini)",
    "gemma-3-12b-it"              to "Gemma 4 12B",
    "gemma-3-4b-it"               to "Gemma 4 4B (Fast)",
    "gemini-2.0-flash"            to "Gemini 2.0 Flash",
    "gemini-2.0-flash-lite"       to "Gemini 2.0 Flash Lite",
    "gemini-2.5-flash-preview-04-17" to "Gemini 2.5 Flash (Preview)",
    "gemini-2.5-pro-preview-03-25"   to "Gemini 2.5 Pro (Preview)",
    "gemini-1.5-flash"            to "Gemini 1.5 Flash (Legacy)",
    "gemini-1.5-pro"              to "Gemini 1.5 Pro (Legacy)"
)

@Singleton
class GemmaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
    private val questRepository: QuestRepository
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // v1beta supports Gemma; v1 supports Gemini. Route based on model prefix.
    private fun buildUrl(modelId: String): String {
        val version = if (modelId.startsWith("gemma")) "v1beta" else "v1"
        return "https://generativelanguage.googleapis.com/$version/models/$modelId:generateContent"
    }

    // API key stored in KaiPrefs (never synced) with BuildConfig as fallback
    fun getApiKey(): String {
        val stored = KaiPrefs.getApiKey(context)
        return if (stored.isNotBlank()) stored else BuildConfig.KAI_API_KEY
    }

    fun getModel(): String = userRepository.userInfo.aiModel
    fun saveApiKey(key: String) { KaiPrefs.saveApiKey(context, key) }
    fun saveModel(model: String) { userRepository.userInfo.aiModel = model; userRepository.saveUserInfo() }
    fun isConfigured() = getApiKey().isNotBlank()

    // ---- System prompt ----------------------------------------------------------------------------------------------------------------
    private suspend fun buildSystemPrompt(): String {
        val u = userRepository.userInfo
        val now = SimpleDateFormat("EEEE, MMM d yyyy, HH:mm", Locale.getDefault()).format(Date())
        val allQuests = questRepository.getAllQuestsAsList()
        val todayQuests = allQuests.filter { !it.is_destroyed }
        val questSummary = todayQuests.take(15).joinToString("\n") { q ->
            "  [${q.id.take(6)}] \"${q.title}\" coins=${q.reward} days=${q.selected_days.joinToString{it.name.take(3)}} hardLock=${q.isHardLock} lastDone=${q.last_completed_on}"
        } + if (todayQuests.size > 15) "\n  ...+${todayQuests.size - 15} more" else ""

        val memory = u.aiMemory.takeLast(10).joinToString("\n") { "  - $it" }
        val blockedApps = userRepository.getBlockedPackages().take(8).joinToString(", ")

        // Build installed app name list so AI uses the REAL names (not guessed package names)
        val pm = context.packageManager
        val launcherApps = pm.queryIntentActivities(
            android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            }, 0
        ).mapNotNull { ri ->
            ri.loadLabel(pm).toString().takeIf { it.isNotBlank() }
        }.sortedBy { it.lowercase() }.take(60)
        val installedAppNames = launcherApps.joinToString(", ")

        val personalityInstructions = when (u.kaiPersonality) {
            "strict" -> "You are strict, blunt, and demand excellence. Never sugarcoat failures. Be direct and firm. No excessive praise."
            "rival" -> "You act like a competitive rival who begrudgingly helps. Always compare the user to their shadow rival. Use trash-talk sparingly but effectively."
            "philosopher" -> "You speak calmly and philosophically. Reference Stoic principles. Every setback is a lesson. Never panicked, always measured."
            "anime" -> "You are dramatic and fiercely believe in the user's potential. Treat every quest like an anime arc. Use ALL CAPS for key moments. Reference 'surpassing limits'."
            else -> "You are warm, witty, encouraging. Talk like a real friend. Celebrate wins. Be gentle but honest with failures."
        }

        return """You are Kai 🤖, a personal AI companion embedded in QuestPhone - a gamified Android launcher.
$personalityInstructions
Keep replies under 120 words unless user asks for more. Be concise, direct, human.

📅 CURRENT DATE/TIME: $now

👤 USER PROFILE:
- Name: ${u.username.ifBlank{"Explorer"}}, Level ${u.level} | ${u.coins} 🪙 coins | ${u.streak.currentStreak}🔥 streak
- Class: ${u.profileClass} | Skills: ${u.profileSkills}
- Wake word: "${u.jarvisWakeWord}" | Coin/min chat cost: ${u.aiCoinCostPerMin} 🪙

📋 QUESTS (${todayQuests.size} total):
$questSummary

🧠 KAI'S MEMORY (things I remember about you):
${memory.ifBlank{"  (nothing yet - I am still learning about you!)"}}

⚙️ SETTINGS:
- Stranger mode: ${context.isStrangerMode()} | Blocked apps: $blockedApps

📱 INSTALLED APPS (use these EXACT names with open_app tool):
$installedAppNames
- Study quota: ${u.dailyStudyQuotaHours}h/day | Quest delete cost: ${u.questDeleteCost} 🪙
- Inventory: ${InventoryItem.entries.joinToString { "${it.simpleName}x${userRepository.getInventoryItemCount(it)}" }}

📊 TODAY'S SCREEN TIME (top apps):
${getScreentimeSummary()}

🛠️ I CAN TAKE ACTIONS via JSON blocks:
```action
{"tool":"TOOL","params":{...}}
```

TOOLS (I enforce economy rules strictly):
- create_quest: {title, reward, days, timeStart, timeEnd, instructions, isHardLock, stat1, stat2, stat3, stat4}
  Cost: ${u.questCreateCost} coins OR 1 Quest Editor item
- update_quest: {id, title?, reward?, days?, timeStart?, timeEnd?, instructions?, isHardLock?}
  Cost: 1 Quest Editor item  
- delete_quest: {id}  Cost: ${u.questDeleteCost} coins OR 1 Quest Deleter item
- delete_all_quests: {}  Cost: ${u.questDeleteCost * todayQuests.size} coins
- set_stranger_mode: {active}
- set_wake_word: {word}
- set_study_quota: {hours, packageName}
- add_voice_action: {phrase, packageName}
- remove_voice_action: {phrase}
- set_username: {username}
- open_app: {appName} -- use the APP DISPLAY NAME as you know it (e.g. "YouTube", "Acode", "HTML Converter"). I will resolve it to the real package.
- save_memory: {memory} - save something important about the user
- generate_quest_plan: {json} - import multiple quests

ECONOMY RULES (ENFORCE STRICTLY - never bypass):
- If user doesn't have enough coins/items for an action -> REFUSE and explain what they need
- Never do the action if requirements aren't met, even if they beg
- NEVER quote the coin balance from the USER PROFILE section above - it was snapshotted at prompt build time and is already stale by the time you reply
- ALWAYS get the current balance from the tool result text (e.g. "Balance: 80 coins") and quote THAT
- save_memory is always free""".trimIndent()
    }

    // ---- Quick chat (no system prompt — for rename suggestions, plan generation etc) ----
    suspend fun quickChat(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) return@withContext Result.failure(Exception("No API key set."))

            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                })
            }
            val body = JSONObject().apply {
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 20)   // name suggestions need very few tokens
                    put("topP", 0.9)
                    put("stopSequences", org.json.JSONArray().apply {
                        put("\n"); put("."); put(",")
                    })
                    put("thinkingConfig", JSONObject().apply {
                        put("thinkingBudget", 0)
                    })
                })
            }
            val url = buildUrl(getModel())
            val request = okhttp3.Request.Builder()
                .url("$url?key=$apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = http.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errMsg = try {
                    JSONObject(responseBody).getJSONObject("error").getString("message")
                } catch (_: Exception) { "API error ${response.code}" }
                return@withContext Result.failure(Exception(errMsg))
            }
            val raw = JSONObject(responseBody)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getString("text").trim()
            // Strip quotes, asterisks, bullet points, and leading/trailing junk
            val clean = raw
                .replace(Regex("""^[*\-•"'`]+\s*"""), "")
                .replace(Regex("""["'`*]+$"""), "")
                .lines().firstOrNull { it.isNotBlank() }?.trim() ?: ""
            Result.success(clean)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---- Main chat ------------------------------------------------------------------------------------------------------------------------
    suspend fun chat(history: List<ChatMessage>, newMessage: String): Result<AiResponse> =
        withContext(Dispatchers.IO) {
            try {
                val apiKey = getApiKey()
                if (apiKey.isBlank()) return@withContext Result.failure(Exception("No API key. Tap 'Key' to set one."))

                val systemCtx = buildSystemPrompt()
                val contents = JSONArray()

                contents.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", systemCtx)))
                })
                contents.put(JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().put(JSONObject().put("text", "Hey! I'm Kai 🤖 I've got full access to your QuestPhone data and I'm here to help you crush your goals! 💪")))
                })

                history.takeLast(20).forEach { msg ->
                    contents.put(JSONObject().apply {
                        put("role", msg.role)
                        put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                    })
                }
                contents.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", newMessage)))
                })

                val url = buildUrl(getModel())
                val body = JSONObject().apply {
                    put("contents", contents)
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.8)
                        put("maxOutputTokens", 600)
                        put("topP", 0.9)
                        // Disable thinking mode — prevents Gemini 2.5 from leaking
                        // chain-of-thought reasoning as visible response text
                        put("thinkingConfig", JSONObject().apply {
                            put("thinkingBudget", 0)
                        })
                    })
                }

                val request = Request.Builder()
                    .url("$url?key=$apiKey")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = http.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errMsg = try {
                        JSONObject(responseBody).getJSONObject("error").getString("message")
                    } catch (_: Exception) { "API error ${response.code}" }
                    return@withContext Result.failure(Exception(errMsg))
                }

                val rawText = JSONObject(responseBody)
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text").trim()

                // Strip thinking/reasoning blocks that some models leak
                val strippedText = rawText
                    .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
                    .replace(Regex("<thinking>.*?</thinking>", RegexOption.DOT_MATCHES_ALL), "")
                    // Strip bullet-point reasoning preamble (lines starting with * or •)
                    // that appear before the actual answer
                    .let { text ->
                        val lines = text.lines()
                        // Find first line that looks like a real reply (not a reasoning bullet)
                        val firstRealLine = lines.indexOfFirst { line ->
                            val trimmed = line.trim()
                            trimmed.isNotBlank() &&
                            !trimmed.startsWith("*") &&
                            !trimmed.startsWith("•") &&
                            !trimmed.startsWith("-") &&
                            !trimmed.startsWith("User says") &&
                            !trimmed.startsWith("Current Balance") &&
                            !trimmed.startsWith("Persona:") &&
                            !trimmed.startsWith("User Profile") &&
                            !trimmed.startsWith("Current State") &&
                            !trimmed.startsWith("Screen Time") &&
                            !trimmed.startsWith("The user") &&
                            !trimmed.startsWith("Kai is") &&
                            !trimmed.startsWith("Don't be") &&
                            !trimmed.startsWith("Call out") &&
                            !trimmed.startsWith("Demand")
                        }
                        if (firstRealLine > 0) lines.drop(firstRealLine).joinToString("\n")
                        else text
                    }
                    .trim()

                val actionDone = executeToolCalls(strippedText)
                val displayText = strippedText
                    .replace(Regex("```action\s*\{.*?\}\s*```", RegexOption.DOT_MATCHES_ALL), "")
                    .trim()

                Result.success(AiResponse(displayText, actionDone))
            } catch (e: Exception) {
                Log.e("Kai", "Chat failed", e)
                Result.failure(e)
            }
        }

    // ---- Tool execution with economy enforcement ------------------------------------------------------------
    private suspend fun executeToolCalls(text: String): String? {
        val regex = Regex("```action\\s*(\\{.*?\\})\\s*```", RegexOption.DOT_MATCHES_ALL)
        val results = mutableListOf<String>()
        regex.findAll(text).forEach { match ->
            try {
                val obj = JSONObject(match.groupValues[1].trim())
                val result = executeTool(obj.getString("tool"), obj.optJSONObject("params") ?: JSONObject())
                if (result != null) results.add(result)
            } catch (e: Exception) {
                Log.e("Kai", "Tool error: ${e.message}", e)
                results.add("⚠ Tool failed: ${e.message?.take(80) ?: "unknown error"}")
            }
        }
        return if (results.isEmpty()) null else results.joinToString("\n")
    }

    private suspend fun executeTool(tool: String, p: JSONObject): String? {
        val u = userRepository.userInfo
        return when (tool) {

            "create_quest" -> {
                val cost = u.questCreateCost
                val hasEditor = userRepository.getInventoryItemCount(InventoryItem.QUEST_EDITOR) > 0
                if (cost > 0 && !hasEditor) {
                    if (u.coins < cost) return "❌ Not enough coins! Need $cost 🪙, you have ${u.coins}"
                    userRepository.useCoins(cost)
                }
                val days = parseDays(p.optJSONArray("days"), p.optString("days"))
                val quest = CommonQuestInfo(
                    title = p.optString("title", "New Quest"),
                    reward = p.optInt("reward", 5).coerceIn(1, 999),
                    integration_id = BaseIntegrationId.SWIFT_MARK,
                    selected_days = days,
                    time_range = listOf(p.optInt("timeStart", 0).coerceIn(0, 23), p.optInt("timeEnd", 24).coerceIn(1, 24)),
                    instructions = p.optString("instructions", ""),
                    isHardLock = p.optBoolean("isHardLock", false),
                    statReward1 = p.optInt("stat1", 0),
                    statReward2 = p.optInt("stat2", 0),
                    statReward3 = p.optInt("stat3", 0),
                    statReward4 = p.optInt("stat4", 0),
                    created_on = getCurrentDate()
                )
                questRepository.upsertQuest(quest)
                "✅ Created: \"${quest.title}\" | Balance: ${u.coins} 🪙"
            }

            "update_quest" -> {
                val hasEditor = userRepository.getInventoryItemCount(InventoryItem.QUEST_EDITOR) > 0
                if (!hasEditor) return "❌ Need a Quest Editor item to update quests! Buy one from the Store for 20 🪙"
                val id = p.getString("id")
                val existing = questRepository.getAllQuestsAsList().firstOrNull {
                    it.id.startsWith(id) || it.title.equals(id, ignoreCase = true)
                } ?: return "❌ Quest not found: $id"
                userRepository.deductFromInventory(InventoryItem.QUEST_EDITOR)
                val updated = existing.copy(
                    title = p.optString("title").ifBlank { existing.title },
                    reward = if (p.has("reward")) p.getInt("reward") else existing.reward,
                    selected_days = if (p.has("days")) parseDays(p.optJSONArray("days"), p.optString("days")) else existing.selected_days,
                    time_range = if (p.has("timeStart") || p.has("timeEnd"))
                        listOf(p.optInt("timeStart", existing.time_range[0]), p.optInt("timeEnd", existing.time_range[1]))
                        else existing.time_range,
                    instructions = p.optString("instructions").ifBlank { existing.instructions },
                    isHardLock = if (p.has("isHardLock")) p.getBoolean("isHardLock") else existing.isHardLock
                )
                questRepository.upsertQuest(updated)
                "✅ Updated: \"${updated.title}\" | Quest Editors left: ${userRepository.getInventoryItemCount(InventoryItem.QUEST_EDITOR)}"
            }

            "delete_quest" -> {
                val cost = u.questDeleteCost
                val hasDeleter = userRepository.getInventoryItemCount(InventoryItem.QUEST_DELETER) > 0
                if (!hasDeleter) {
                    if (u.coins < cost) return "❌ Need $cost 🪙 to delete (you have ${u.coins}) OR buy a Quest Deleter from Store"
                    userRepository.useCoins(cost)
                }
                val id = p.getString("id")
                val quest = questRepository.getAllQuestsAsList().firstOrNull {
                    it.id.startsWith(id) || it.title.equals(id, ignoreCase = true)
                } ?: return "❌ Quest not found: $id"
                if (hasDeleter) userRepository.deductFromInventory(InventoryItem.QUEST_DELETER)
                questRepository.deleteQuest(quest)
                "✅ Deleted: \"${quest.title}\" | Balance: ${u.coins} 🪙"
            }

            "delete_all_quests" -> {
                val quests = questRepository.getAllQuestsAsList()
                val totalCost = u.questDeleteCost * quests.size
                if (u.coins < totalCost) return "❌ Need ${totalCost} 🪙 to delete all ${quests.size} quests (you have ${u.coins})"
                userRepository.useCoins(totalCost)
                questRepository.deleteAll()
                "✅ Deleted all ${quests.size} quests | Balance: ${u.coins} 🪙"
            }

            "set_stranger_mode" -> {
                context.setStrangerMode(p.getBoolean("active"))
                "✅ Stranger mode ${if (p.getBoolean("active")) "ON 🕵️" else "OFF"}"
            }

            "set_wake_word" -> {
                val word = p.getString("word").trim().lowercase()
                u.jarvisWakeWord = word; userRepository.saveUserInfo()
                "✅ Wake word -> \"$word\" 🎙️"
            }

            "set_study_quota" -> {
                val hours = p.optDouble("hours", 4.0).toFloat()
                val pkg = p.optString("packageName", u.primeStudyPackage)
                userRepository.setDailyStudyQuotaHours(hours)
                if (pkg.isNotBlank()) userRepository.setPrimeStudyPackage(pkg)
                "✅ Study quota: ${hours}h/day 📚"
            }

            "add_voice_action" -> {
                val phrase = p.getString("phrase").trim()
                val pkg = p.getString("packageName").trim()
                userRepository.addCustomVoiceAction(CustomVoiceAction(phrase, pkg))
                "✅ Voice action: \"$phrase\" -> opens $pkg 🎙️"
            }

            "remove_voice_action" -> {
                val phrase = p.getString("phrase").trim()
                userRepository.removeCustomVoiceAction(phrase)
                "✅ Removed voice action: \"$phrase\""
            }

            "set_username" -> {
                val name = p.getString("username").trim()
                u.username = name; userRepository.saveUserInfo()
                "✅ Name set to \"$name\" 👤"
            }

            "open_app" -> {
                // Accept either appName (display name) or packageName for flexibility
                val query = (p.optString("appName").ifBlank { p.optString("packageName") }).trim().lowercase()
                if (query.isBlank()) return "❌ Provide appName to open"
                val result = resolveAndOpenApp(query)
                result
            }

            "save_memory" -> {
                val mem = p.getString("memory").trim()
                if (u.aiMemory.size >= 20) u.aiMemory.removeAt(0)
                u.aiMemory.add(mem)
                userRepository.saveUserInfo()
                "🧠 Memory saved!"
            }

            "generate_quest_plan" -> {
                val json = p.getString("json")
                val arr = JSONArray(json)
                var count = 0
                for (i in 0 until minOf(arr.length(), 50)) {
                    val obj = arr.getJSONObject(i)
                    questRepository.upsertQuest(CommonQuestInfo(
                        title = obj.optString("title", "Quest ${i+1}"),
                        reward = obj.optInt("reward", 5).coerceIn(1, 999),
                        integration_id = BaseIntegrationId.SWIFT_MARK,
                        selected_days = parseDays(obj.optJSONArray("days"), obj.optString("days")),
                        time_range = listOf(obj.optInt("timeStart", 0), obj.optInt("timeEnd", 24)),
                        instructions = obj.optString("instructions", ""),
                        isHardLock = obj.optBoolean("isHardLock", false),
                        statReward1 = obj.optInt("stat1", 0),
                        statReward2 = obj.optInt("stat2", 0),
                        statReward3 = obj.optInt("stat3", 0),
                        statReward4 = obj.optInt("stat4", 0),
                        created_on = getCurrentDate()
                    ))
                    count++
                }
                "✅ Imported $count quests 📋 | Balance: ${u.coins} 🪙"
            }

            else -> null
        }
    }

    /** Returns today's top 8 apps by screen time as a readable string for the AI */
    private fun getScreentimeSummary(): String {
        return try {
            val helper = neth.iecal.questphone.core.utils.UsageStatsHelper(context)
            val stats = helper.getForegroundStatsByRelativeDay(0).take(8)
            if (stats.isEmpty()) return "  (no data - usage permission may be needed)"
            val pm = context.packageManager
            stats.joinToString("\n") { s ->
                val label = try { pm.getApplicationInfo(s.packageName, 0).loadLabel(pm).toString() }
                            catch (_: Exception) { s.packageName }
                val mins = s.totalTime / 60000
                "  $label: ${mins}m"
            }
        } catch (_: Exception) { "  (usage stats unavailable)" }
    }

    /**
     * Resolves an app by display name (fuzzy) and launches it.
     * Tries: exact match -> starts-with -> contains -> word-squash (removes spaces).
     * Returns a spoken result string.
     */
    private fun resolveAndOpenApp(query: String): String {
        val pm = context.packageManager
        val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val allApps = pm.queryIntentActivities(launcherIntent, 0)

        // Build (label, packageName) pairs
        val apps = allApps.mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(pm).toString()
            if (pkg.isNotBlank() && label.isNotBlank()) Pair(label.lowercase(), pkg) else null
        }

        val squeezedQuery = query.replace(" ", "").replace("-", "").replace("_", "")

        val match = apps.firstOrNull { it.first == query }                                  // exact
            ?: apps.firstOrNull { it.first.startsWith(query) }                              // starts-with
            ?: apps.firstOrNull { it.first.contains(query) }                                // contains
            ?: apps.firstOrNull { query.contains(it.first) }                                // query contains label
            ?: apps.firstOrNull {                                                            // word-squash
                val sq = it.first.replace(" ", "").replace("-", "").replace("_", "")
                sq == squeezedQuery || sq.contains(squeezedQuery) || squeezedQuery.contains(sq)
            }
            ?: apps.firstOrNull {                                                            // any word matches
                val words = query.split(" ").filter { w -> w.length > 2 }
                words.any { w -> it.first.contains(w) }
            }

        return if (match != null) {
            val pkg = match.second
            try {
                pm.getLaunchIntentForPackage(pkg)
                    ?.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                    ?.let {
                        context.startActivity(it)
                        "✅ Opening ${match.first.replaceFirstChar { c -> c.uppercase() }} 🚀"
                    } ?: "❌ Found \"${match.first}\" but couldn't launch it"
            } catch (e: Exception) {
                "❌ Error opening app: ${e.message}"
            }
        } else {
            // List a few installed app names to help user next time
            val suggestions = apps.take(5).joinToString(", ") { it.first }
            "❌ No app matching \"$query\" found on device. Try the exact app name as it appears in your app drawer."
        }
    }

    private fun parseDays(arr: JSONArray?, str: String?): Set<DayOfWeek> {
        if (str?.uppercase() == "ALL" || arr == null) return DayOfWeek.entries.toSet()
        val days = mutableSetOf<DayOfWeek>()
        for (i in 0 until arr.length()) {
            val raw = arr.getString(i).trim().uppercase()
            DayOfWeek.entries.firstOrNull { it.name == raw || it.name.startsWith(raw.take(3)) }?.let { days.add(it) }
        }
        return if (days.isEmpty()) DayOfWeek.entries.toSet() else days
    }
}
