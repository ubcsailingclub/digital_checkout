package com.ubcsc.checkout.ui.craft

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubcsc.checkout.R
import com.ubcsc.checkout.ui.theme.AvailableGreen
import com.ubcsc.checkout.ui.theme.CardBlue
import com.ubcsc.checkout.ui.theme.DeepOcean
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.ui.theme.DividerColor
import com.ubcsc.checkout.ui.theme.TealLight
import com.ubcsc.checkout.ui.theme.TealMid
import com.ubcsc.checkout.ui.theme.TextMuted
import com.ubcsc.checkout.ui.theme.UnavailableRed
import com.ubcsc.checkout.ui.util.CraftImageMapper
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.Craft
import com.ubcsc.checkout.viewmodel.Member
import kotlinx.coroutines.delay

private const val INACTIVITY_TIMEOUT_MS = 30_000L

private val categoryOrder = listOf("SAILING", "WINDSURF", "KAYAK & SUP")

private fun craftCategory(craftClass: String): String {
    val upper = craftClass.uppercase().trim()
    return when {
        upper.startsWith("WINDSURFER") -> "WINDSURF"
        upper in setOf("RS QUEST", "RS QUEST SPINNAKER", "LASER", "VANGUARD 15", "RS500", "RS800", "HOBIE 16", "NACRA F18") -> "SAILING"
        upper.startsWith("KAYAK") || upper == "SUP" -> "KAYAK & SUP"
        else -> "OTHER"
    }
}

private data class FleetGroup(
    val craftClass: String,
    val availableCount: Int,
    val totalCount: Int,
    val soonestReturn: java.time.LocalTime? = null   // earliest ETR among checked-out boats
)

@Composable
fun CraftSelectScreen(member: Member, crafts: List<Craft>, viewModel: CheckoutViewModel) {
    LaunchedEffect(Unit) {
        delay(INACTIVITY_TIMEOUT_MS)
        viewModel.resetToIdle()
    }
    CraftSelectContent(
        memberName    = member.name,
        crafts        = crafts,
        onFleetSelect = { fleetClass -> viewModel.onFleetSelected(member, fleetClass) },
        onCancel      = { viewModel.goBack() }
    )
}

@Composable
private fun CraftSelectContent(
    memberName: String,
    crafts: List<Craft>,
    onFleetSelect: (String) -> Unit,
    onCancel: () -> Unit
) {
    val groupedFleets = crafts
        .groupBy { it.craftClass }
        .map { (cls, boats) ->
            FleetGroup(
                craftClass     = cls,
                availableCount = boats.count { it.isAvailable },
                totalCount     = boats.size,
                soonestReturn  = boats
                    .filter { !it.isAvailable }
                    .mapNotNull { it.expectedReturnTime }
                    .minOrNull()
            )
        }
        .groupBy { craftCategory(it.craftClass) }
        .entries
        .sortedBy { (cat, _) ->
            categoryOrder.indexOf(cat).let { if (it < 0) Int.MAX_VALUE else it }
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepOcean)
    ) {
        // Top accent bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(TealMid, TealLight, TealMid)))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.craft_select_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = memberName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TealLight,
                        letterSpacing = 0.5.sp
                    )
                }
                TextButton(onClick = onCancel) {
                    Text(
                        "← Back",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .height(1.dp)
                    .background(DividerColor)
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                groupedFleets.forEach { (category, fleets) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CategoryHeader(category)
                    }
                    items(fleets, key = { it.craftClass }) { fleet ->
                        FleetCard(
                            fleet    = fleet,
                            onSelect = { if (fleet.availableCount > 0) onFleetSelect(fleet.craftClass) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = TealLight,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FleetCard(fleet: FleetGroup, onSelect: () -> Unit) {
    val hasAvailable = fleet.availableCount > 0
    val accentColor  = if (hasAvailable) TealMid  else UnavailableRed
    val cardAlpha    = if (hasAvailable) 1f        else 0.45f

    Surface(
        onClick        = onSelect,
        enabled        = hasAvailable,
        modifier       = Modifier
            .height(168.dp)
            .alpha(cardAlpha),
        shape          = RoundedCornerShape(16.dp),
        color          = CardBlue,
        tonalElevation = if (hasAvailable) 6.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = 0.6f), accentColor.copy(alpha = 0.1f))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Boat icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeepOcean.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter        = painterResource(CraftImageMapper.getDrawableRes(fleet.craftClass)),
                        contentDescription = fleet.craftClass,
                        modifier       = Modifier.size(62.dp),
                        contentScale   = ContentScale.Fit,
                        colorFilter    = if (hasAvailable) CraftImageMapper.filterAvailable
                                         else CraftImageMapper.filterUnavailable
                    )
                }

                // Fleet name
                Text(
                    text       = fleet.craftClass,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White,
                    maxLines   = 1,
                    textAlign  = TextAlign.Center
                )

                // Availability count pill
                AvailabilityChip(
                    available     = fleet.availableCount,
                    total         = fleet.totalCount,
                    soonestReturn = fleet.soonestReturn
                )
            }
        }
    }
}

@Composable
private fun AvailabilityChip(
    available: Int,
    total: Int,
    soonestReturn: java.time.LocalTime? = null
) {
    val hasAny = available > 0
    val color  = if (hasAny) AvailableGreen else UnavailableRed
    val label  = when {
        hasAny -> "$available / $total available"
        soonestReturn != null ->
            "Back by ${soonestReturn.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))}"
        else -> "All out"
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.labelSmall,
            color      = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true, backgroundColor = 0xFF0D1B2A)
@Composable
private fun CraftSelectPreview() {
    DigitalCheckoutTheme {
        CraftSelectContent(
            memberName = "Alex Sailor",
            crafts = listOf(
                Craft("1",  "QT01", "Quest #1",    "RS Quest",    true),
                Craft("2",  "QT02", "Quest #2",    "RS Quest",    true),
                Craft("3",  "LZ01", "Laser #1",    "Laser",       true),
                Craft("4",  "LZ02", "Laser #2",    "Laser",       false),
                Craft("5",  "VG01", "Vanguard #1", "Vanguard 15", true),
                Craft("6",  "R501", "RS500 #1",    "RS500",       true),
                Craft("7",  "R801", "RS800 #1",    "RS800",       false),
                Craft("8",  "HB01", "Hobie #1",    "Hobie 16",    true),
                Craft("9",  "F181", "Nacra #1",    "Nacra F18",   true),
                Craft("10", "WS01", "L1",          "Windsurfer",  true),
                Craft("11", "WS02", "L2",          "Windsurfer",  true),
                Craft("12", "WS03", "L3",          "Windsurfer",  false),
                Craft("13", "KY01", "Kayak #1",    "Kayak",       true),
                Craft("14", "KY02", "Kayak #2",    "Kayak",       true),
                Craft("15", "SP01", "SUP #1",      "SUP",         true),
            ),
            onFleetSelect = {}, onCancel = {}
        )
    }
}
