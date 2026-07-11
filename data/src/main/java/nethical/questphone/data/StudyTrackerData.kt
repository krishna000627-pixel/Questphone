package nethical.questphone.data

import kotlinx.serialization.Serializable

@Serializable
data class StudyClass(
    val id: String = "",
    val name: String = "",          // e.g. "Physics Batch A"
    val subject: String = "",
    val durationHours: Float = 2f,
    val daysOfWeek: List<Int> = emptyList(), // 1=Mon … 7=Sun
    val startTime: String = "09:00"  // HH:mm
)

@Serializable
data class MockTestScore(
    val id: String = "",
    val examName: String = "",
    val date: String = "",           // yyyy-MM-dd
    val totalMarks: Int = 0,
    val obtainedMarks: Int = 0,
    val subject: String = "Full",    // subject or "Full" for full mock
    val notes: String = ""
)
