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
import com.ubcsc.checkout.ui.theme.TextSecondary
import com.ubcsc.checkout.ui.theme.UnavailableRed
import com.ubcsc.checkout.ui.util.CraftImageMapper
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.Craft
import com.ubcsc.checkout.viewmodel.Member
import kotlinx.coroutines.delay

private const val BOAT_INACTIVITY_TIMEOUT_MS = 30_000L

@Composable
fun BoatSelectScreen(
    member:     Member,
    fleetClass: String,
    crafts:     List<Craft>,
    viewModel:  CheckoutViewModel
) {
    LaunchedEffect(Unit) {
        delay(BOAT_INACTIVITY_TIMEOUT_MS)
        viewModel.resetToIdle()
    }
    BoatSelectContent(
        memberName   = member.name,
        fleetClass   = fleetClass,
        crafts       = crafts,
        onBoatSelect = { craft -> viewModel.onCraftSelected(member, craft) },
        onCancel     = { viewModel.onCancel() }
    )
}

@Composable
private fun BoatSelectContent(
    memberName:   String,
    fleetClass:   String,
    crafts:       List<Craft>,
    onBoatSelect: (Craft) -> Unit,
    onCancel:     () -> Unit
) {
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
                        text       = fleetClass,
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                    Text(
                        text         = memberName,
                        style        = MaterialTheme.typography.bodyMedium,
                        color        = TealLight,
                        letterSpacing = 0.5.sp
                    )
                }
                TextButton(onClick = onCancel) {
                    Text(
                        stringResource(R.string.cancel_button),
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
                columns            = GridCells.Adaptive(minSize = 200.dp),
                contentPadding     = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement   = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(crafts) { craft ->
                    BoatCard(
                        craft    = craft,
                        onSelect = { if (craft.isAvailable) onBoatSelect(craft) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoatCard(craft: Craft, onSelect: () -> Unit) {
    val available   = craft.isAvailable
    val accentColor = if (available) TealMid   else UnavailableRed
    val iconTint    = if (available) TealLight  else TextMuted
    val cardAlpha   = if (available) 1f         else 0.45f

    Surface(
        onClick        = onSelect,
        enabled        = available,
        modifier       = Modifier
            .height(168.dp)
            .alpha(cardAlpha),
        shape          = RoundedCornerShape(16.dp),
        color          = CardBlue,
        tonalElevation = if (available) 6.dp else 0.dp
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
                // Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeepOcean.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter            = painterResource(CraftImageMapper.getDrawableRes(craft.craftClass)),
                        contentDescription = craft.craftClass,
                        modifier           = Modifier.size(60.dp),
                        contentScale       = ContentScale.Fit,
                        colorFilter        = ColorFilter.tint(iconTint)
                    )
                }

                // Boat name
                Text(
                    text       = craft.displayName,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White,
                    maxLines   = 1
                )

                // Status
                BoatStatusChip(available = available)
            }
        }
    }
}

@Composable
private fun BoatStatusChip(available: Boolean) {
    val color = if (available) AvailableGreen else UnavailableRed
    val label = if (available) stringResource(R.string.available) else stringResource(R.string.unavailable)

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
private fun BoatSelectPreview() {
    DigitalCheckoutTheme {
        BoatSelectContent(
            memberName = "Alex Sailor",
            fleetClass = "Laser",
            crafts = listOf(
                Craft("5", "LZ01", "Laser #1", "Laser", true),
                Craft("6", "LZ02", "Laser #2", "Laser", true),
                Craft("7", "LZ03", "Laser #3", "Laser", false),
                Craft("8", "LZ04", "Laser #4", "Laser", true),
            ),
            onBoatSelect = {}, onCancel = {}
        )
    }
}
