package com.ubcsc.checkout.ui.craft

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ubcsc.checkout.R
import com.ubcsc.checkout.ui.theme.CoralRed
import com.ubcsc.checkout.ui.theme.DigitalCheckoutTheme
import com.ubcsc.checkout.ui.theme.NavyBlue
import com.ubcsc.checkout.ui.theme.SeaGreen
import com.ubcsc.checkout.viewmodel.CheckoutViewModel
import com.ubcsc.checkout.viewmodel.Craft
import com.ubcsc.checkout.viewmodel.Member
import kotlinx.coroutines.delay

private const val INACTIVITY_TIMEOUT_MS = 30_000L

@Composable
fun CraftSelectScreen(
    member: Member,
    crafts: List<Craft>,
    viewModel: CheckoutViewModel
) {
    LaunchedEffect(Unit) {
        delay(INACTIVITY_TIMEOUT_MS)
        viewModel.resetToIdle()
    }

    CraftSelectContent(
        crafts = crafts,
        onCraftSelected = { craft -> viewModel.onCraftSelected(member, craft) },
        onCancel = { viewModel.onCancel() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CraftSelectContent(
    crafts: List<Craft>,
    onCraftSelected: (Craft) -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.craft_select_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = NavyBlue
                )
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(stringResource(R.string.cancel_button))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(crafts) { craft ->
                    CraftCard(craft = craft, onClick = { if (craft.isAvailable) onCraftSelected(craft) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CraftCard(craft: Craft, onClick: () -> Unit) {
    val availableColor = if (craft.isAvailable) SeaGreen else CoralRed
    val cardAlpha = if (craft.isAvailable) 1f else 0.5f

    Card(
        onClick = onClick,
        enabled = craft.isAvailable,
        modifier = Modifier
            .height(140.dp)
            .padding(2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            disabledContainerColor = Color.White.copy(alpha = cardAlpha)
        ),
        border = BorderStroke(2.dp, availableColor.copy(alpha = cardAlpha)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (craft.isAvailable) 3.dp else 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsBoat,
                contentDescription = null,
                tint = availableColor.copy(alpha = cardAlpha),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = craft.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NavyBlue.copy(alpha = cardAlpha)
            )
            Text(
                text = craft.craftClass,
                style = MaterialTheme.typography.bodySmall,
                color = NavyBlue.copy(alpha = 0.6f * cardAlpha)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (craft.isAvailable) stringResource(R.string.available)
                       else stringResource(R.string.unavailable),
                style = MaterialTheme.typography.labelSmall,
                color = availableColor.copy(alpha = cardAlpha),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(widthDp = 960, heightDp = 600, showBackground = true)
@Composable
private fun CraftSelectPreview() {
    DigitalCheckoutTheme {
        CraftSelectContent(
            crafts = listOf(
                Craft("1", "LZ01", "Laser #1", "Laser", true),
                Craft("2", "LZ02", "Laser #2", "Laser", true),
                Craft("3", "LZ03", "Laser #3", "Laser", false),
                Craft("4", "470-01", "470 #1", "470", true),
                Craft("5", "WS01", "Windsurfer #1", "Windsurfer", true),
            ),
            onCraftSelected = {},
            onCancel = {}
        )
    }
}
