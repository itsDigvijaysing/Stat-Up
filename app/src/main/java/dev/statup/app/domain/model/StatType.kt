package dev.statup.app.domain.model

import androidx.compose.ui.graphics.Color
import dev.statup.app.ui.theme.*

/**
 * The six stats. [blurb] and [examples] are the plain-language definitions shown under the
 * stat pickers and on the How It Works screen - without them nothing in the app told a new
 * user what a stat actually means. Wording matches `database/README.md`, which is also the
 * spec the offline task classifier was trained against, so the two never drift apart.
 *
 * Colours stay in `ui/theme/Color.kt` (house rule) and are only referenced here.
 */
enum class StatType(
    val displayName: String,
    val color: Color,
    val blurb: String,
    val examples: String
) {
    STR(
        "Strength", StatSTR,
        "Physical effort and pushing your body",
        "gym, running, sport, chores, heavy lifting"
    ),
    INT(
        "Intelligence", StatINT,
        "Learning and solving problems",
        "study, exams, coding, research, technical reading"
    ),
    WIS(
        "Wisdom", StatWIS,
        "Staying on top of your life",
        "bills, renewals, records, planning, journaling"
    ),
    DEX(
        "Dexterity", StatDEX,
        "Skill and precision built by repetition",
        "instruments, art, craft, technique drills"
    ),
    CHA(
        "Charisma", StatCHA,
        "Connecting with people",
        "calls, messages, meetings, networking, presenting"
    ),
    VIT(
        "Vitality", StatVIT,
        "Maintaining your body",
        "sleep, meals, water, rest, medication, check-ups"
    );

    companion object {
        fun fromString(value: String): StatType? = entries.find { it.name == value }
    }
}
