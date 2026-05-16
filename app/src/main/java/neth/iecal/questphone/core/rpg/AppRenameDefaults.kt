package neth.iecal.questphone.core.rpg

/**
 * Default app rename sets built from device package list.
 * Three styles: RPG (fantasy), Friendly (clean labels), Minimal (short).
 *
 * System is extensible — add more styles by adding a new map below.
 * Apply via Settings → RPG Mode or programmatically via AppNameResolver.
 *
 * Generated from device package list — update by adding entries here.
 */
object AppRenameDefaults {

    /** Returns the rename map for a given style key. */
    fun forStyle(style: String): Map<String, String> = when (style) {
        "rpg"      -> RPG_NAMES
        "friendly" -> FRIENDLY_NAMES
        "minimal"  -> MINIMAL_NAMES
        else       -> emptyMap()
    }

    val STYLES = listOf("rpg", "friendly", "minimal")
    val STYLE_LABELS = mapOf(
        "rpg"      to "⚔️ RPG",
        "friendly" to "😊 Friendly",
        "minimal"  to "◻ Minimal"
    )

    /**
     * Apply a style: merges into userInfo.appRenames.
     * Existing manual renames are NOT overwritten.
     */
    fun applyStyle(
        style: String,
        userRepository: neth.iecal.questphone.backed.repositories.UserRepository,
        overwriteExisting: Boolean = false
    ) {
        val map = forStyle(style)
        val renames = userRepository.userInfo.appRenames
        map.forEach { (pkg, name) ->
            if (overwriteExisting || !renames.containsKey(pkg)) {
                renames[pkg] = name
            }
        }
        userRepository.saveUserInfo()
    }

    /** Clear all renames that came from a style (those whose value matches the style map). */
    fun clearStyle(
        style: String,
        userRepository: neth.iecal.questphone.backed.repositories.UserRepository
    ) {
        val map = forStyle(style)
        val renames = userRepository.userInfo.appRenames
        map.forEach { (pkg, name) ->
            if (renames[pkg] == name) renames.remove(pkg)
        }
        userRepository.saveUserInfo()
    }

    val RPG_NAMES: Map<String, String> = mapOf(
        "app.lawnchair.lawnfeed" to "Outer Realm Feed",
        "arivihan.technologies.doubtbuzzter2" to "Scholar's Den",
        "bitpit.launcher" to "Outer Realm",
        "com.android.chrome" to "Iron Browser",
        "com.android.contacts" to "Fellowship",
        "com.android.fmradio" to "Ether Radio",
        "com.android.settings" to "Sanctum",
        "com.android.vending" to "Emporium",
        "com.anthropic.claude" to "Ancient Scholar",
        "com.chess" to "Tactician's Board",
        "com.coloros.alarmclock" to "Sundial",
        "com.coloros.calculator" to "Alchemist's Scale",
        "com.coloros.compass2" to "Navigator",
        "com.coloros.filemanager" to "Vault",
        "com.coloros.gallery3d" to "Gallery",
        "com.coloros.phonemanager" to "System Warden",
        "com.coloros.smartsidebar" to "Quick Runes",
        "com.coloros.soundrecorder" to "Echo Stone",
        "com.coloros.weather2" to "Storm Oracle",
        "com.community.oneroom" to "Shadow Stream",
        "com.convertly.imagetopdf" to "Scroll Forge",
        "com.csdroid.pkg" to "Package Oracle",
        "com.discord" to "Guild Hall",
        "com.dts.freefiremax" to "Battleground",
        "com.fampay.in" to "Youth Coin",
        "com.foxdebug.acodefree" to "Runic Forge",
        "com.github.cvzi.wallpaperexport" to "Realm Exporter",
        "com.google.android.apps.bard" to "Oracle",
        "com.google.android.apps.googleassistant" to "Spirit Advisor",
        "com.google.android.apps.maps" to "Cartographer",
        "com.google.android.apps.messaging" to "Raven Post",
        "com.google.android.apps.nbu.files" to "Archive",
        "com.google.android.apps.nbu.paisa.user" to "Gold Ledger",
        "com.google.android.apps.photos" to "Memory Vault",
        "com.google.android.apps.restore" to "Time Warden",
        "com.google.android.apps.wellbeing" to "Life Compass",
        "com.google.android.calendar" to "Chronicle",
        "com.google.android.contacts" to "Fellowship",
        "com.google.android.documentsui" to "Archive Gate",
        "com.google.android.gm" to "Royal Courier",
        "com.google.android.googlequicksearchbox" to "All-Knowing Eye",
        "com.google.android.keep" to "Tome of Notes",
        "com.google.android.tts" to "Voice Weaver",
        "com.google.android.youtube" to "Bard's Stage",
        "com.google.android.youtube.music" to "Lute of Ages",
        "com.google.ar.lens" to "Arcane Lens",
        "com.heytap.market" to "Market",
        "com.heytap.music" to "Bard's Lute",
        "com.heytap.themestore" to "Realm Skins",
        "com.innersloth.spacemafia" to "Void Betrayal",
        "com.instagram.android" to "Mirror Realm",
        "com.instantbits.cast.webvideo" to "Crystal Cast",
        "com.jio.jioplay.tv" to "Ethereal Screen",
        "com.jpl.jiomart" to "Market Hall",
        "com.ludo.king" to "King's Game",
        "com.miniclip.carrom" to "Disc Arena",
        "com.mmicunovic.papercopy" to "Mirror Print",
        "com.openai.chatgpt" to "The Sage",
        "com.oplus.camera" to "Vision Crystal",
        "com.oplus.linker" to "PC Bridge",
        "com.oplus.multiapp" to "Clone Realm",
        "com.oplus.onet" to "Net Warden",
        "com.oplus.safecenter" to "Shield",
        "com.sameerasw.essentials" to "Core Runes",
        "com.snaptube.premium" to "Shadow Download",
        "com.spotify.music" to "Siren's Song",
        "com.technore.xrptunnelplus" to "Void Tunnel",
        "com.ted.number" to "Number Sage",
        "com.termux" to "Dark Terminal",
        "com.truedevelopersstudio.automatictap.autoclicker" to "Phantom Touch",
        "com.whatsapp" to "Scroll of Messages",
        "com.whatsapp.w4b" to "Merchant's Scroll",
        "com.whereismytrain.android" to "Iron Dragon Tracker",
        "com.wssc.simpleclock" to "Time Keeper",
        "com.x8zs.sandbox" to "Mirror World",
        "com.xupstudio.volumefinetuner" to "Sound Crystal",
        "de.onyxbits.listmyapps" to "App Chronicler",
        "de.szalkowski.activitylauncher" to "Rune Launcher",
        "flar2.homebutton" to "Rune Mapper",
        "free.vpn.unblock.proxy.turbovpn" to "Shadow Cloak",
        "hesoft.T2S" to "Voice Scroll",
        "imagetopdf.pdfconverter.jpgtopdf.pdfeditor" to "Page Alchemist",
        "in.startv.hotstar" to "Crystal Vision",
        "lsafer.edgeseek" to "Edge Seeker",
        "neth.iecal.questphone" to "QuestPhone",
        "org.telegram.messenger" to "Cipher Relay",
        "ru.zdevs.zarchiver" to "Vault Keeper",
        "se.dirac.acs" to "Sound Forge",
        "tech.butterfly.app" to "Sage's Canvas",
    )
    val FRIENDLY_NAMES: Map<String, String> = mapOf(
        "arivihan.technologies.doubtbuzzter2" to "Arivihan",
        "com.android.chrome" to "Chrome",
        "com.android.contacts" to "Contacts",
        "com.android.fmradio" to "FM Radio",
        "com.android.settings" to "Settings",
        "com.android.vending" to "Play Store",
        "com.anthropic.claude" to "Claude",
        "com.chess" to "Chess",
        "com.coloros.alarmclock" to "Clock",
        "com.coloros.calculator" to "Calculator",
        "com.coloros.compass2" to "Compass",
        "com.coloros.filemanager" to "Files",
        "com.coloros.gallery3d" to "Gallery",
        "com.coloros.soundrecorder" to "Recorder",
        "com.coloros.weather2" to "Weather",
        "com.discord" to "Discord",
        "com.dts.freefiremax" to "Free Fire",
        "com.fampay.in" to "FamPay",
        "com.foxdebug.acodefree" to "Code Editor",
        "com.google.android.apps.bard" to "Gemini",
        "com.google.android.apps.googleassistant" to "Assistant",
        "com.google.android.apps.maps" to "Maps",
        "com.google.android.apps.messaging" to "Messages",
        "com.google.android.apps.nbu.files" to "Files",
        "com.google.android.apps.nbu.paisa.user" to "GPay",
        "com.google.android.apps.photos" to "Photos",
        "com.google.android.apps.wellbeing" to "Wellbeing",
        "com.google.android.calendar" to "Calendar",
        "com.google.android.contacts" to "Contacts",
        "com.google.android.gm" to "Mail",
        "com.google.android.googlequicksearchbox" to "Google",
        "com.google.android.keep" to "Notes",
        "com.google.android.youtube" to "YouTube",
        "com.heytap.market" to "App Market",
        "com.heytap.music" to "Music",
        "com.heytap.themestore" to "Themes",
        "com.innersloth.spacemafia" to "Among Us",
        "com.instagram.android" to "Insta",
        "com.jio.jioplay.tv" to "JioTV",
        "com.jpl.jiomart" to "JioMart",
        "com.ludo.king" to "Ludo",
        "com.miniclip.carrom" to "Carrom",
        "com.openai.chatgpt" to "ChatGPT",
        "com.oplus.camera" to "Camera",
        "com.snaptube.premium" to "Snaptube",
        "com.termux" to "Terminal",
        "com.truedevelopersstudio.automatictap.autoclicker" to "Auto Clicker",
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WA Business",
        "com.whereismytrain.android" to "Train Tracker",
        "com.x8zs.sandbox" to "Sandbox",
        "free.vpn.unblock.proxy.turbovpn" to "TurboVPN",
        "hesoft.T2S" to "Text to Speech",
        "in.startv.hotstar" to "Hotstar",
        "neth.iecal.questphone" to "QuestPhone",
        "org.telegram.messenger" to "Telegram",
        "ru.zdevs.zarchiver" to "ZArchiver",
        "tech.butterfly.app" to "Manus",
    )
    val MINIMAL_NAMES: Map<String, String> = mapOf(
        "com.android.chrome" to "Browser",
        "com.android.settings" to "Config",
        "com.android.vending" to "Store",
        "com.anthropic.claude" to "AI",
        "com.coloros.alarmclock" to "Clock",
        "com.coloros.calculator" to "Calc",
        "com.discord" to "Server",
        "com.foxdebug.acodefree" to "Code",
        "com.google.android.apps.bard" to "AI",
        "com.google.android.apps.maps" to "Map",
        "com.google.android.apps.messaging" to "SMS",
        "com.google.android.apps.nbu.paisa.user" to "Pay",
        "com.google.android.apps.photos" to "Gallery",
        "com.google.android.calendar" to "Cal",
        "com.google.android.gm" to "Mail",
        "com.google.android.keep" to "Notes",
        "com.google.android.youtube" to "Video",
        "com.heytap.music" to "Music",
        "com.instagram.android" to "Feed",
        "com.openai.chatgpt" to "GPT",
        "com.oplus.camera" to "Cam",
        "com.termux" to "Shell",
        "com.whatsapp" to "Chat",
        "com.whatsapp.w4b" to "Biz Chat",
        "neth.iecal.questphone" to "QP",
        "org.telegram.messenger" to "TG",
    )
}
