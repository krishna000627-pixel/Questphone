package neth.iecal.questphone.core.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class QuestSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val EXTRA_IS_FIRST_TIME = "is_first_time"
        const val EXTRA_IS_PULL_SPECIFIC_QUEST = "is_for_specific_quest"
    }
    override suspend fun doWork(): Result {
        return Result.success()
    }
}
