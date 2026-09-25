package dev.statup.app.ui.components.rpg

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.statup.app.domain.model.Quote
import dev.statup.app.ui.components.glass.GlassCard
import dev.statup.app.ui.theme.AccentPrimary
import dev.statup.app.ui.theme.Inter
import dev.statup.app.ui.theme.TextPrimary
import dev.statup.app.ui.theme.TextTertiary

/**
 * The day's quote, rendered as a glass card under the status window. Renders nothing
 * while the quote is still resolving (no skeleton needed - resolution is instant for the
 * offline pack and one small request for online sources).
 *
 * When [Quote.attribution] is present it is rendered in the footer - ZenQuotes' free tier
 * REQUIRES visible attribution, so never strip that line.
 */
@Composable
fun DailyQuoteCard(quote: Quote?, modifier: Modifier = Modifier) {
    if (quote == null) return

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "“${quote.text}”",
                color = TextPrimary,
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                fontFamily = Inter,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            val signature = buildString {
                append("- ").append(quote.author)
                quote.origin?.let { append(" · ").append(it) }
            }
            Text(
                text = signature,
                color = AccentPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter,
                modifier = Modifier.align(Alignment.End)
            )

            quote.attribution?.let { provider ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Quote of the day · $provider",
                    color = TextTertiary,
                    fontSize = 9.sp,
                    fontFamily = Inter,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
