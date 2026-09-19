package neth.iecal.questphone.core.utils.termux

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * QuestPhone <-> Termux integration.
 *
 * This replaces the old in-app fake terminal (which tried to `ProcessBuilder`-exec
 * directly into Termux's private app directory — something normal Android sandboxing
 * does not allow without root). Instead, this talks to the real Termux app through its
 * public `RUN_COMMAND` intent API (the same mechanism Termux:Tasker / Termux:Widget use).
 *
 * For this to work, two things must be true on the device:
 *   1. QuestPhone must hold the `com.termux.permission.RUN_COMMAND` runtime permission
 *      (declared in AndroidManifest.xml, requested at runtime like any dangerous permission).
 *   2. Termux must have `allow-external-apps = true` set in
 *      `~/.termux/termux.properties` — Termux ignores RUN_COMMAND intents from any other
 *      app until this is set. [SETUP_SCRIPT] below configures this for the user.
 */
object TermuxIntegration {

    const val TERMUX_PACKAGE = "com.termux"
    const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"

    /** Path used for the launcher's own entry-point script inside Termux's home dir. */
    const val LAUNCHER_SCRIPT_PATH =
        "/data/data/com.termux/files/home/.shortcuts/questphone.sh"

    fun isTermuxInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    fun hasRunCommandPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, RUN_COMMAND_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    private const val PREFS_NAME = "termux_bridge_prefs"
    private const val PREF_ENABLED = "bridge_enabled"

    /** Whether the localhost HTTP bridge ([QuestPhoneLocalServer]) should be running.
     *  Persisted so [neth.iecal.questphone.core.utils.receiver.BootReceiver] can restart
     *  it after a reboot if the user had it on. */
    fun isBridgeEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false)

    fun setBridgeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_ENABLED, enabled).apply()
        val intent = Intent(context, QuestPhoneLocalServer::class.java)
        if (enabled) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.stopService(intent)
        }
    }

    /**
     * Opens Termux in the foreground and runs [path] (defaults to the launcher's own
     * shortcut script created by [SETUP_SCRIPT]). Requires [hasRunCommandPermission] and
     * Termux's `allow-external-apps` to already be set — otherwise Termux silently ignores
     * the intent.
     */
    fun openForegroundSession(
        context: Context,
        path: String = LAUNCHER_SCRIPT_PATH,
        args: Array<String> = emptyArray(),
        workDir: String = "/data/data/com.termux/files/home"
    ) {
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra("com.termux.RUN_COMMAND_PATH", path)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args)
            putExtra("com.termux.RUN_COMMAND_WORKDIR", workDir)
            // false = interactive/visible session rather than a headless background task
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            // "0" = start a new session and switch to it (brings Termux to the foreground)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        }
        context.startForegroundService(intent)
    }

    /** Just brings the Termux app to the foreground with no command, as a fallback. */
    fun launchTermuxApp(context: Context) {
        context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)?.let {
            context.startActivity(it)
        }
    }

    fun copySetupScriptToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("questphone_setup.sh", SETUP_SCRIPT))
    }

    /** Port QuestPhoneLocalServer listens on, 127.0.0.1 only. Kept in sync with
     *  QuestPhoneLocalServer.PORT — duplicated here so this file has no Hilt/Android
     *  service dependency and stays trivial to read/copy as a plain string. */
    const val LOCAL_SERVER_PORT = 8342

    /**
     * One-time setup script the user pastes and runs inside Termux itself — the
     * "master script": installs anything missing (curl), turns on
     * `allow-external-apps`, writes the session entry-point script, and installs the
     * full `questphone` shell function. Cannot be run by QuestPhone directly — Termux's
     * sandbox means QuestPhone has no write access to Termux's home directory until
     * `allow-external-apps` is already on, which is exactly what this script turns on.
     * Bootstrapping this always requires one manual step inside Termux.
     *
     * What it does:
     *   1. Installs `curl` via `pkg` if it's missing.
     *   2. Sets `allow-external-apps = true` in termux.properties so Termux accepts
     *      RUN_COMMAND intents from other apps.
     *   3. Creates ~/.shortcuts/questphone.sh — the entry point QuestPhone opens via
     *      [openForegroundSession]. Every new session run this way prints a separator
     *      and an automatic `questphone home` summary (quests, coins, stats) with no
     *      typing needed.
     *   4. Adds the `questphone` shell function to ~/.bashrc — quests, coins, stats,
     *      inventory, an interactive quest-creation wizard, opening apps/screens by
     *      name, and a coin-spend confirmation prompt before opening anything blocked.
     */
    val SETUP_SCRIPT: String = """
        #!/data/data/com.termux/files/usr/bin/bash
        set -e
        echo "==> Setting up QuestPhone <-> Termux integration"

        if ! command -v curl >/dev/null 2>&1; then
            echo "==> Installing curl..."
            pkg install -y curl
        fi

        PROP_FILE="${'$'}HOME/.termux/termux.properties"
        mkdir -p "${'$'}HOME/.termux"
        touch "${'$'}PROP_FILE"

        if grep -q "^allow-external-apps" "${'$'}PROP_FILE" 2>/dev/null; then
            sed -i 's/^allow-external-apps.*/allow-external-apps = true/' "${'$'}PROP_FILE"
        else
            echo "allow-external-apps = true" >> "${'$'}PROP_FILE"
        fi
        termux-reload-settings 2>/dev/null || true

        mkdir -p "${'$'}HOME/.shortcuts"
        cat > "${'$'}HOME/.shortcuts/questphone.sh" <<'SCRIPT_EOF'
        #!/data/data/com.termux/files/usr/bin/bash
        echo "──────────────────────────────────────────"
        echo " QuestPhone session • $(date '+%Y-%m-%d %H:%M')"
        echo "──────────────────────────────────────────"
        source "${'$'}HOME/.bashrc" 2>/dev/null
        if command -v questphone >/dev/null 2>&1; then
            questphone home
        else
            echo "questphone function not found — re-run the setup script."
        fi
        echo
        exec bash
        SCRIPT_EOF
        chmod +x "${'$'}HOME/.shortcuts/questphone.sh"

        BASHRC="${'$'}HOME/.bashrc"
        touch "${'$'}BASHRC"
        if ! grep -q "^# >>> questphone bridge" "${'$'}BASHRC" 2>/dev/null; then
            cat >> "${'$'}BASHRC" <<'SCRIPT_EOF'

        # >>> questphone bridge
        _qp_confirm_open() {
            local endpoint="${'$'}1"
            local payload="${'$'}2"
            local resp
            resp=${'$'}(curl -s -X POST "${'$'}BASE/${'$'}endpoint" -d "${'$'}payload")
            if echo "${'$'}resp" | grep -q '"locked":true'; then
                local cost mins ans
                cost=${'$'}(echo "${'$'}resp" | sed -n 's/.*"cost_coins":\([0-9]*\).*/\1/p')
                mins=${'$'}(echo "${'$'}resp" | sed -n 's/.*"unlock_minutes":\([0-9]*\).*/\1/p')
                printf "Blocked. Spend %s coins to unlock for %s min? [y/N] " "${'$'}cost" "${'$'}mins"
                read -r ans < /dev/tty
                case "${'$'}ans" in
                    y|Y) curl -s -X POST "${'$'}BASE/${'$'}endpoint" -d "${'$'}(echo "${'$'}payload" | sed 's/}$/,"confirm":true}/')" ;;
                    *) echo "Cancelled." ;;
                esac
            else
                echo "${'$'}resp"
            fi
        }

        questphone() {
            local BASE="http://127.0.0.1:$LOCAL_SERVER_PORT"
            local all_args="${'$'}*"
            local cmd="${'$'}1"; shift || true
            local qtype qtitle qmin qdesc qurl extra

            case "${'$'}cmd" in
                quests)      curl -s "${'$'}BASE/quests" ;;
                home)        curl -s "${'$'}BASE/home" ;;
                quest)       curl -s "${'$'}BASE/quests/${'$'}1" ;;
                complete)    curl -s -X POST "${'$'}BASE/quests/${'$'}1/complete" ;;
                create)
                    if [ -n "${'$'}1" ]; then
                        curl -s -X POST "${'$'}BASE/quests" -d "${'$'}1"
                    else
                        echo "Quest type:"
                        echo "  1) Swift Mark            - quick manual check-off"
                        echo "  2) Deep Focus            - timed focus session"
                        echo "  3) AI Snap               - photo-verified"
                        echo "  4) External Integration  - finish setup in-app"
                        echo "  5) Optional Quest        - doesn't count toward streak"
                        printf "Pick 1-5 [1]: "; read -r qtype < /dev/tty; qtype="${'$'}{qtype:-1}"
                        printf "Title: "; read -r qtitle < /dev/tty
                        extra=""
                        case "${'$'}qtype" in
                            2) printf "Duration in minutes [25]: "; read -r qmin < /dev/tty
                               extra=",\"duration_minutes\":${'$'}{qmin:-25}" ;;
                            3) printf "What should the photo show?: "; read -r qdesc < /dev/tty
                               extra=",\"task_description\":\"${'$'}qdesc\"" ;;
                            4) printf "URL: "; read -r qurl < /dev/tty
                               extra=",\"external_url\":\"${'$'}qurl\"" ;;
                        esac
                        curl -s -X POST "${'$'}BASE/quests" -d "{\"title\":\"${'$'}qtitle\",\"type\":${'$'}qtype${'$'}extra}"
                    fi
                    ;;
                balance)     curl -s "${'$'}BASE/balance" ;;
                stats)       curl -s "${'$'}BASE/stats" ;;
                inventory)   curl -s "${'$'}BASE/inventory" ;;
                apps)        curl -s "${'$'}BASE/apps" ;;
                hidden)      curl -s "${'$'}BASE/apps/hidden" ;;
                open)        _qp_confirm_open "apps/open" "{\"package\":\"${'$'}1\"}" ;;
                screen)      curl -s -X POST "${'$'}BASE/open/${'$'}1" ;;
                "")          echo "usage: questphone {quests|home|quest <id>|complete <id>|create|balance|stats|inventory|apps|hidden|open <app>|screen <name>|<name, e.g. 'ascension hall' or 'whatsapp'>}" ;;
                # anything else: fuzzy-match against in-app screens, then installed
                # apps by label — this is what makes `questphone ascension hall`
                # and `questphone whatsapp` just work. Blocked apps ask before
                # spending coins, same as `open`.
                *)           _qp_confirm_open "launch" "{\"query\":\"${'$'}all_args\"}" ;;
            esac
            echo
        }
        # <<< questphone bridge
        SCRIPT_EOF
        fi

        echo "==> Done."
        echo "==> Now go to Android Settings > Apps > QuestPhone > Permissions and"
        echo "==> allow the 'Run commands in Termux' permission if prompted."
        echo "==> In QuestPhone's launcher settings, toggle the Termux bridge on, then"
        echo "==> restart Termux (or 'source ~/.bashrc') and try: questphone home"
    """.trimIndent()
}
