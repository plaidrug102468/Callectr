/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package app.callectr.recorder

import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import app.callectr.recorder.ui.common.SettingsSidebar
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import app.callectr.recorder.data.AppPreferences
import app.callectr.recorder.onboarding.OnboardingStatus
import app.callectr.recorder.ui.navigation.AppScreen
import app.callectr.recorder.ui.navigation.HomeSection
import app.callectr.recorder.ui.navigation.NotificationDestination
import app.callectr.recorder.utils.AppLogger
import app.callectr.recorder.ui.screens.DisclaimerScreen
import app.callectr.recorder.ui.screens.HomeScreen
import app.callectr.recorder.ui.screens.PersonalHomeScreen
import app.callectr.recorder.ui.screens.PersonalSettingsScreen
import app.callectr.recorder.ui.screens.PermissionsScreen
import app.callectr.recorder.ui.screens.SettingsScreen
import app.callectr.recorder.ui.screens.WizardScreen
import app.callectr.recorder.ui.theme.CallVaultTheme
import app.callectr.recorder.ui.viewmodels.AppNavigationViewModel
import app.callectr.recorder.ui.viewmodels.HomeViewModel
import app.callectr.recorder.ui.viewmodels.SettingsViewModel

/**
 * Top-level router composable called from [MainActivity].
 *
 * Decides which of three destinations to display - the one-time disclaimer, the permissions
 * checklist, or the main settings, wraps every destination in the app theme compose.
 *
 * ## State flow
 * - [AppNavigationViewModel] is the "Brain" for routing: it owns [AppNavigationViewModel.onboardingStatus]
 *   and decides which screen is active.
 * - [SettingsViewModel] is the "Brain" for settings: it owns [SettingsViewModel.updateTrigger]
 *   and all user-preference persistence.
 * - Both expose `StateFlow`s observed via [collectAsState] - the "bridge" that watches a data
 *   stream and triggers a refresh (recompose) whenever a value changes.
 * - [LocalLifecycleOwner] is the object that tells this composable whether the current screen
 *   is visible, in the background, or being destroyed. We attach a [LifecycleEventObserver]
 *   via [DisposableEffect] to refresh state whenever the user returns to the app (e.g. after
 *   granting a permission in the system Settings app).
 */
@Composable
fun AppNavigationScreen(
    notificationDestination: NotificationDestination = NotificationDestination.None,
    onNotificationDestinationHandled: () -> Unit = {},
    openRecording: String? = null,
    onOpenRecordingHandled: () -> Unit = {},
    openTranscript: String? = null,
    onOpenTranscriptHandled: () -> Unit = {}
) {

    val activityContext = LocalContext.current

    /** [LocalLifecycleOwner] provides the lifecycle of the current screen (Activity/Fragment).
     *  We observe it so we know when the user navigates back to the app. */
    val lifecycleOwner = LocalLifecycleOwner.current

    // AppNavigationViewModel - the "Brain" for routing: owns onboarding state.
    val appNavViewModel: AppNavigationViewModel = viewModel()

    // SettingsViewModel - the "Brain" for settings: owns theme + preference state.
    val settingsViewModel: SettingsViewModel = viewModel()

    /**
     * [collectAsState] bridges the [AppNavigationViewModel.onboardingStatus] `StateFlow` to Compose.
     * Every time the flow emits a new [OnboardingStatus.Status] value, Compose triggers a
     * refresh (recompose) so [resolveScreen] picks the correct destination.
     */
    val onboardingStatus by appNavViewModel.onboardingStatus.collectAsState()

    /**
     * [collectAsState] bridges the [AppNavigationViewModel.manualScreen] `StateFlow` to Compose.
     * When non-null (and onboarding has resolved to Home), it overrides the resolved screen so the
     * user can step into Settings — there is no Jetpack NavHost, so this is the manual nav mechanism.
     */
    val manualScreen by appNavViewModel.manualScreen.collectAsState()

    /**
     * [collectAsState] bridges the [SettingsViewModel.updateTrigger] `StateFlow` to Compose.
     * Reading allow us to trigger a refresh (recompose) whenever the user changes a setting that requires a
     * major UI update (e.g. theme change) that can only be updated here in the AppNavigationScreen.
     */
    val settingsViewModelUpdateTrigger by settingsViewModel.updateTrigger.collectAsState() // reading .value here is required so it trigger a recomposition as soon as it changes.

    // AppPreferences is used to read preferences directly.
    val preferences = settingsViewModel.preferences

    // Listen for refresh in the SettingsViewModel, as certain settings changes may change some checks in the onboarding status.
    LaunchedEffect(settingsViewModelUpdateTrigger) {
        val newStatus = OnboardingStatus.getStatus(activityContext, preferences)
        if (newStatus != onboardingStatus) {
            appNavViewModel.refresh()
        }
    }

    // resolveScreen reads the flow-backed onboardingStatus - no direct preference reads here,
    // which is what caused the stale-state bug that existed before this architecture.
    val resolvedScreen = resolveScreen(onboardingStatus)

    // The manual override (e.g. Settings) is only honoured once onboarding has fully resolved to Home.
    // If onboarding state regresses (e.g. a permission is revoked), the override is ignored so the
    // router pulls the user back into the correct onboarding step.
    val screenState = if (resolvedScreen == AppScreen.Home && manualScreen != null) manualScreen!! else resolvedScreen

    // [DisposableEffect] attaches a [LifecycleEventObserver] to [lifecycleOwner].
    // When the user returns to the app (ON_RESUME), both ViewModels refresh so the screen
    // reflects any changes made while the app was in the background (e.g. permission granted).
    // [onDispose] removes the observer to prevent leaks when this composable leaves the tree.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appNavViewModel.refresh()
                settingsViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Personal Callectr has one home surface instead of the old Hub/Recordings/Transcripts/
    // Summaries sections. Notification taps simply return to that home surface.
    LaunchedEffect(notificationDestination, screenState) {
        if (notificationDestination == NotificationDestination.None) return@LaunchedEffect
        AppLogger.d("CV:Nav", "Opened from the \${notificationDestination.key} notification; showing personal home")
        onNotificationDestinationHandled()
    }

    // A request to land on one recording cannot outlive the visit it arrived in, for the same
    // reason a notification's cannot: held unacknowledged, it would fire whenever onboarding
    // finally resolved to Home, which could be days and several screens later. Home acknowledges it
    // itself once it has somewhere to put it; here we only drop the ones Home will never see.
    LaunchedEffect(openRecording, screenState) {
        if (openRecording == null || screenState == AppScreen.Home) return@LaunchedEffect
        AppLogger.d("CV:Nav", "Asked to open a recording during $screenState; dropping the request")
        onOpenRecordingHandled()
    }

    // The same guard for a transcript asked for by name, and for the same reason: a request held
    // unacknowledged would fire whenever onboarding finally resolved to Home, which could be days
    // and several screens later. Home acknowledges it itself once it has somewhere to put it.
    LaunchedEffect(openTranscript, screenState) {
        if (openTranscript == null) return@LaunchedEffect
        AppLogger.d("CV:Nav", "Ignoring legacy transcript request in personal Callectr")
        onOpenTranscriptHandled()
    }

    // Derive the active theme from AppPreferences so a theme change triggers a refresh (recompose)
    // and is applied immediately.
    val darkTheme = when ( preferences.getThemeMode()) {
        AppPreferences.ThemeMode.LIGHT -> false
        AppPreferences.ThemeMode.DARK   -> true
        AppPreferences.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val dynamicColor = preferences.isDynamicColorEnabled()

    // -------- Show the right screen
    CallVaultTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        when (screenState) {

            AppScreen.Disclaimer -> DisclaimerScreen(
                onContinue = {
                    preferences.setDisclaimerAccepted(true)
                    appNavViewModel.refresh()
                }
            )

            AppScreen.Permissions -> PermissionsScreen(
                status              = onboardingStatus,
                onPermissionGranted = { appNavViewModel.refresh() }
            )

            AppScreen.Wizard -> WizardScreen(
                // The wizard persists everything + flips the wizardCompleted flag on Finish; refreshing
                // the onboarding status here advances the router from Wizard to Home.
                onFinished = { appNavViewModel.refresh() }
            )

            // Settings is a PANEL over Home, not a destination: Home stays composed underneath, so
            // closing is instant and nothing behind is rebuilt. SettingsSidebar owns back, the scrim
            // tap and the swipe; the gear only has to open it.
            AppScreen.Home -> {
                val homeViewModel: HomeViewModel = viewModel()

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) homeViewModel.refresh()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                PersonalHomeScreen(
                    onOpenSettings = { appNavViewModel.navigateTo(AppScreen.Settings) },
                    openRecording = openRecording,
                    onOpenRecordingHandled = onOpenRecordingHandled,
                    viewModel = homeViewModel
                )
            }

            AppScreen.Settings -> {
                BackHandler { appNavViewModel.navigateBack() }
                PersonalSettingsScreen(
                    onBack = { appNavViewModel.navigateBack() }
                )
            }
        }
    }
}

// -------- Private helpers

/**
 * Maps an [OnboardingStatus.Status] snapshot to the [AppScreen] that should be visible.
 *
 * The logic is intentionally linear:
 *  1. Disclaimer first — the user must accept before anything else is shown.
 *  2. Permissions next — every required permission must be granted.
 *  3. Wizard next — the one-time setup wizard must be completed.
 *  4. Home last — the default landing screen once everything is set up.
 *
 * Settings is intentionally NOT produced here; it is reached only via manual navigation
 * (see [AppNavigationViewModel.navigateTo]).
 *
 * Relying on [OnboardingStatus.Status] (part of the `StateFlow`) instead of reading preferences
 * directly ensures that each acceptance/grant emits a new value through the flow, which
 * triggers a refresh (recompose) in [AppNavigationScreen] and advances the user automatically.
 *
 * @param status The latest snapshot emitted by [AppNavigationViewModel.onboardingStatus].
 * @return The [AppScreen] that matches the user's current setup progress.
 */
private fun resolveScreen(status: OnboardingStatus.Status): AppScreen {
    return when {
        !status.disclaimerAccepted -> AppScreen.Disclaimer
        !status.isComplete()       -> AppScreen.Permissions
        !status.wizardCompleted    -> AppScreen.Wizard
        else                       -> AppScreen.Home
    }
}
