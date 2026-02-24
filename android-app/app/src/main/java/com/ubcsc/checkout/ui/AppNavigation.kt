package com.ubcsc.checkout.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ubcsc.checkout.ui.confirm.ConfirmScreen
import com.ubcsc.checkout.ui.craft.BoatSelectScreen
import com.ubcsc.checkout.ui.craft.CraftSelectScreen
import com.ubcsc.checkout.ui.crew.AddCrewScreen
import com.ubcsc.checkout.ui.idle.IdleScreen
import com.ubcsc.checkout.ui.member.MemberScreen
import com.ubcsc.checkout.ui.result.ResultScreen
import com.ubcsc.checkout.viewmodel.CheckoutUiState
import com.ubcsc.checkout.viewmodel.CheckoutViewModel

object Routes {
    const val IDLE = "idle"
    const val MEMBER = "member"
    const val CRAFT_SELECT = "craft_select"
    const val BOAT_SELECT  = "boat_select"
    const val CREW = "crew"
    const val CONFIRM = "confirm"
    const val RESULT = "result"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: CheckoutViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Drive navigation from ViewModel state
    LaunchedEffect(uiState) {
        when (uiState) {
            is CheckoutUiState.Idle -> navController.navigate(Routes.IDLE) {
                popUpTo(0) { inclusive = true }
            }
            is CheckoutUiState.Loading -> { /* stay on current screen */ }
            is CheckoutUiState.MemberFound -> navController.navigate(Routes.MEMBER) {
                popUpTo(Routes.IDLE)
            }
            is CheckoutUiState.SelectingCraft -> navController.navigate(Routes.CRAFT_SELECT) {
                popUpTo(Routes.MEMBER)
            }
            is CheckoutUiState.SelectingBoat -> navController.navigate(Routes.BOAT_SELECT) {
                popUpTo(Routes.CRAFT_SELECT)
            }
            is CheckoutUiState.AddingCrew,
            is CheckoutUiState.AwaitingCrewCard -> navController.navigate(Routes.CREW) {
                popUpTo(Routes.BOAT_SELECT)
            }
            is CheckoutUiState.ConfirmCheckout,
            is CheckoutUiState.ConfirmCheckin -> navController.navigate(Routes.CONFIRM) {
                popUpTo(Routes.MEMBER)
            }
            is CheckoutUiState.Success,
            is CheckoutUiState.Error -> navController.navigate(Routes.RESULT) {
                popUpTo(Routes.IDLE)
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.IDLE) {

        composable(Routes.IDLE) {
            IdleScreen(viewModel = viewModel)
        }

        composable(Routes.MEMBER) {
            val state = uiState as? CheckoutUiState.MemberFound ?: return@composable
            MemberScreen(
                member = state.member,
                viewModel = viewModel
            )
        }

        composable(Routes.CRAFT_SELECT) {
            val state = uiState as? CheckoutUiState.SelectingCraft ?: return@composable
            CraftSelectScreen(
                member = state.member,
                crafts = state.crafts,
                viewModel = viewModel
            )
        }

        composable(Routes.BOAT_SELECT) {
            val state = uiState as? CheckoutUiState.SelectingBoat ?: return@composable
            BoatSelectScreen(
                member     = state.member,
                fleetClass = state.fleetClass,
                crafts     = state.crafts,
                viewModel  = viewModel
            )
        }

        composable(Routes.CREW) {
            AddCrewScreen(uiState = uiState, viewModel = viewModel)
        }

        composable(Routes.CONFIRM) {
            ConfirmScreen(uiState = uiState, viewModel = viewModel)
        }

        composable(Routes.RESULT) {
            ResultScreen(uiState = uiState, viewModel = viewModel)
        }
    }
}
