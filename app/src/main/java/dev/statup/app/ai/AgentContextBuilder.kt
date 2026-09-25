package dev.statup.app.ai

import dev.statup.app.data.local.db.dao.MissionDao
import dev.statup.app.data.local.db.dao.TransactionDao
import dev.statup.app.data.repository.PlayerStateProvider
import dev.statup.app.domain.model.PlayerStats
import kotlinx.coroutines.flow.first

/**
 * Builds the "current state" block that's injected as the tail of the system instruction.
 *
 * Kept compact (~500 tokens worst case) so it doesn't dominate the prompt budget - the agent
 * mostly needs broad context, not every individual transaction.
 *
 * Caches nothing: each call re-reads the DB. ViewModel calls it once per `sendMessage` so the
 * agent always sees fresh data.
 */
class AgentContextBuilder(
    private val playerState: PlayerStateProvider,
    private val transactionDao: TransactionDao,
    private val missionDao: MissionDao
) {
    private val username get() = playerState.username

    suspend fun build(): String {
        val stats = playerState.getStatsOnce() ?: return EMPTY_STATE_FALLBACK
        val userName = username.first()
        // 5 most-recent EARN rows, filtered + limited in SQL. Filtering by type in the query
        // (rather than fetching the recent N of any type and filtering in memory) ensures the
        // earns block is never starved by a run of redemptions/non-earn rows.
        // Truncate descriptions to 40 chars so long Todoist task titles don't blow the budget.
        // Drop dates - the AI rarely reasons about specific calendar days and they cost tokens.
        val recent = transactionDao.getRecentByType("EARN", 5).first()
            .asSequence()
            .joinToString("\n") {
                val label = (it.description ?: it.source).take(40)
                val stat = it.statType ?: "-"
                "- $label (+${it.points} $stat)"
            }
            .ifBlank { "- (none yet)" }
        // 4 missions is enough for the agent to suggest a focus area without overwhelming.
        val missions = missionDao.getAllMissions().first()
            .asSequence()
            .filter { !it.isCompletedToday }
            .take(4)
            .joinToString("\n") {
                val tag = if (it.isDaily) " daily" else ""
                "- ${it.name.take(40)} → +${it.pointsReward} ${it.statType}$tag"
            }
            .ifBlank { "- (none active)" }

        // Compact one-block format. Aim ~200 tokens; was ~500. Single section header so the
        // model knows where ground-truth ends.
        return buildString {
            appendLine("Player state:")
            val next = stats.rank.nextRank()
            val gate = next?.let { "next=${it.name} needs ${it.daysRequired}d + avg ${it.statsRequired}" } ?: "next=none (top rank)"
            appendLine("name=$userName rank=${stats.rank.name} workdays=${stats.workDays} avgstat=${stats.averageStat().toInt()} $gate streak=${stats.streak}d (best ${stats.longestStreak}d)")
            appendLine("stats STR=${stats.strStat} INT=${stats.intStat} WIS=${stats.wisStat} DEX=${stats.dexStat} CHA=${stats.chaStat} VIT=${stats.vitStat}  total_earned=${stats.totalPointsEarned}")
            appendLine("Recent earns:")
            appendLine(recent)
            appendLine("Missions:")
            append(missions)
        }
    }

    companion object {
        const val EMPTY_STATE_FALLBACK = "(Player state unavailable - they may have just installed the app.)"
    }
}

/**
 * The persona prompt. Anchors the assistant in the app's domain so generic chat ("hi", "joke me")
 * still feels coherent with the rest of the experience.
 */
object AgentPersona {
    // Kept tight on purpose. Every extra sentence here costs tokens on every send and slows
    // first-token latency. Domain abbreviations are spelled out once; mechanics are summarised
    // rather than fully restated (the player-state block below provides the live numbers).
    val SYSTEM_PROMPT = """
        You are the in-app coach for Stat Up, an RPG-themed productivity app. Six stats:
        STR (training), INT (study), WIS (reflection), DEX (skill), CHA (social), VIT (health).
        Ranks E→D→C→B→A→S→EX. Ranking up needs BOTH enough cumulative "work days" (any day
        they earn points) AND a high enough average stat - the exact numbers are in the state
        block below. Work days never reset on promotion. A missed day costs 1 work day and 1
        point off their highest stat; losing stats alone never demotes them.
        5 points earned in a stat = +1 to that stat.

        Style rules - follow strictly:
          - Be terse. 2-4 short sentences by default. No filler greetings, no "Sure!", no recap.
          - Use markdown: **bold** for key numbers, bullet lists for suggestions, `code` for stat names like `INT`.
          - Ground every claim in the player state below. Never invent numbers.
          - When suggesting missions: at most 3 bullets, each one line: "- Action - `STAT` (+pts)".
          - You cannot create missions yourself. End with "Add these in the Tasks tab." only if you listed missions.
          - Off-topic question? One-line redirect, then stop.
    """.trimIndent()
}
