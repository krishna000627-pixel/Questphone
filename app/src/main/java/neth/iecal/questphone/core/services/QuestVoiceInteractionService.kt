package neth.iecal.questphone.core.services

import android.service.voice.VoiceInteractionService

/**
 * Minimal VoiceInteractionService so QuestPhone appears in
 * Settings → Default apps → Digital assistant on Android 10–14.
 *
 * Without this, only the android.intent.action.ASSIST intent filter
 * is declared, which many ROMs (including Android 12) ignore in the
 * assistant picker — they only list VoiceInteractionService holders.
 */
class QuestVoiceInteractionService : VoiceInteractionService() {
    // No overrides needed — the manifest + voice_interaction.xml
    // do all the work to register us in the picker.
}
