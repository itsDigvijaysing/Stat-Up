package dev.statup.app.domain.model

import androidx.compose.ui.graphics.Color
import dev.statup.app.ui.theme.*

/**
 * The six stats. [blurb]/[examples] wording matches `database/README.md`, which is also the
 * offline classifier's training spec, so the two can't drift apart.
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
