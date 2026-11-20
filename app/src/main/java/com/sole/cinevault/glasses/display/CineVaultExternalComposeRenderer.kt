package com.sole.cinevault.glasses.display

import com.sole.cinevault.glasses.GlassesFilmOwner
import android.app.Activity
import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sole.cinevault.CineVaultRoot
import com.sole.cinevault.GlassesCalibrationPrompt
import com.sole.cinevault.needsCalibration
import com.sole.cinevault.glasses.halo.HaloCanonicalCineVaultSurface
import com.sole.cinevault.glasses.halo.HaloRenderDestinationPolicy
import com.sole.cinevault.ui.theme.CineVaultTheme

/**
 * External rendering enters the SAME canonical CineVault session/root.
 *
 * R1 hardens Halo ownership at the render-destination boundary: only this
 * EXTERNAL_DISPLAY composition may enable the Halo cursor/receiver.
 */
class CineVaultExternalComposeRenderer(
    private val activity: Activity,
) : ExternalCineVaultRenderer {

    private var presentation: CineVaultExternalPresentation? = null
    private var attachedDisplayId: Int? = null

    override fun attach(displayId: Int) {
        if (attachedDisplayId == displayId && presentation?.isShowing == true) return
        detach()
        val display = findDisplay(displayId) ?: return
        presentation = CineVaultExternalPresentation(activity, display).also { it.show() }
        attachedDisplayId = displayId
    }

    override fun detach() {
        presentation?.dismiss()
        presentation = null
        attachedDisplayId = null
    }

    internal fun isAttachedTo(displayId: Int): Boolean =
        attachedDisplayId == displayId && presentation?.isShowing == true

    private fun findDisplay(displayId: Int): Display? {
        val manager = activity.getSystemService(DisplayManager::class.java)
        return manager?.displays?.firstOrNull { it.displayId == displayId }
    }
}

private class CineVaultExternalPresentation(
    private val hostActivity: Activity,
    display: Display,
) : Presentation(hostActivity, display),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner,
    OnBackPressedDispatcherOwner,
    ActivityResultRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    // A Presentation is a plain Dialog, so it has no back-button owner of its own. The
    // full CineVault tree uses BackHandler, which crashed with "No
    // OnBackPressedDispatcherOwner" the moment the glasses showed the real app.
    // Same story for launchers (rememberLauncherForActivityResult): borrow the phone
    // activity's registry so file pickers etc. work from the glasses copy too.
    override val activityResultRegistry: ActivityResultRegistry
        get() = (hostActivity as ComponentActivity).activityResultRegistry

    private val backDispatcher = OnBackPressedDispatcher()
    override val onBackPressedDispatcher: OnBackPressedDispatcher get() = backDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        savedStateController.performAttach()
        savedStateController.performRestore(savedInstanceState)
        super.onCreate(savedInstanceState)

        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        ExternalViewportSessionState.bindProfile(
            profileName = display.name,
            restoredTransform =
                ExternalViewportProfilePrefs.load(
                    context = context,
                    displayName = display.name,
                ),
            onTransformChanged = { transform ->
                ExternalViewportProfilePrefs.save(
                    context = context,
                    displayName = display.name,
                    transform = transform,
                )
            },
        )

        GlassesScreenSize.bind(context, display.name)
        com.sole.cinevault.glasses.halo.PadScrollPrefs.load(context)
        GlassesBackBridge.handler = { backDispatcher.onBackPressed() }

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@CineVaultExternalPresentation)
            setViewTreeSavedStateRegistryOwner(this@CineVaultExternalPresentation)
            setViewTreeViewModelStoreOwner(this@CineVaultExternalPresentation)

            setContent {
                CineVaultTheme {
                    CompositionLocalProvider(
                        LocalCineVaultRenderDestination provides
                            CineVaultRenderDestination.EXTERNAL_DISPLAY,
                        LocalOnBackPressedDispatcherOwner provides
                            this@CineVaultExternalPresentation,
                        LocalActivityResultRegistryOwner provides
                            this@CineVaultExternalPresentation,
                    ) {
                        CineVaultSessionRoot {
                            val configuration = LocalConfiguration.current
                            var showCalibration by remember(display.name) {
                                mutableStateOf(
                                    needsCalibration(
                                        context = context,
                                        displayName = display.name,
                                    )
                                )
                            }

                            // Settings -> "Recalibrate" shows the card again.
                            val recalibrateStamp = com.sole.cinevault.glasses.GlassesReplay.calibrationStamp
                            androidx.compose.runtime.LaunchedEffect(recalibrateStamp) {
                                if (recalibrateStamp != 0L) showCalibration = true
                            }

                            val haloEnabled =
                                HaloRenderDestinationPolicy.shouldOwnHalo(
                                    CineVaultRenderDestination.EXTERNAL_DISPLAY
                                )

                            // Everything (the app, the film, the calibration card) lives INSIDE
                            // the Halo surface so the glasses pointer can reach all of it.
                            HaloCanonicalCineVaultSurface(
                                enabled = haloEnabled,
                                acceptRemoteInput = haloEnabled,
                            ) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                                    // Shrunk toward the centre so edges the glasses crop stay visible.
                                    // Touch hit-testing follows the scaling, so the pointer stays accurate.
                                    // A 3D side-by-side film keeps the full frame (depth would break if
                                    // shrunk); everything else follows the chosen size.
                                    val appScale =
                                        if (GlassesFilmOwner.externalShowsPlayer) {
                                            GlassesScreenSizePolicy.effective(
                                                chosen = GlassesScreenSize.scale,
                                                fullFrame3d = ExternalPlayerHudState.fullFrame3d,
                                            )
                                        } else {
                                            GlassesScreenSize.scale
                                        }
                                    Box(
                                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                            scaleX = appScale
                                            scaleY = appScale
                                        },
                                    ) {
                                        CineVaultRoot()
                                    }

                                    if (GlassesFilmOwner.externalShowsPlayer) {
                                        ExternalStereoNoticeOverlay()
                                    }

                                    ExternalCanonicalVideoLayer()

                                    GlassesCalibrationPrompt(
                                        visible = showCalibration,
                                        displayName = display.name,
                                        isLandscape =
                                            configuration.orientation ==
                                                android.content.res.Configuration.ORIENTATION_LANDSCAPE,
                                        onDone = { showCalibration = false },
                                        modifier = Modifier.align(Alignment.Center),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        setContentView(composeView)
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onStop() {
        // Dismissing marks the lifecycle DESTROYED first; Android then calls onStop and a
        // DESTROYED lifecycle can never go back to CREATED (that crashed on unplug and on
        // closing the app). Only step down when we are not already finished.
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
        super.onStop()
    }

    private var finished = false

    override fun dismiss() {
        if (finished) return
        finished = true
        GlassesBackBridge.handler = null
        ExternalViewportSessionState.unbindProfile(display.name)
        super.dismiss() // runs onStop while the lifecycle is still alive
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
