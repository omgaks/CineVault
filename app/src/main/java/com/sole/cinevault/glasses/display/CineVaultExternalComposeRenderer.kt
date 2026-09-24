package com.sole.cinevault.glasses.display

import android.app.Activity
import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sole.cinevault.CineVaultRoot
import com.sole.cinevault.glasses.halo.HaloCanonicalCineVaultSurface
import com.sole.cinevault.ui.theme.CineVaultTheme

/**
 * D2-8:
 * external rendering still enters the SAME canonical CineVault session/root,
 * now through the shared Halo integration boundary.
 *
 * No glasses-only player/navigation/subtitle tree exists here.
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
        presentation = CineVaultExternalPresentation(activity, display).also {
            it.show()
        }
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
    activity: Activity,
    display: Display,
) : Presentation(activity, display),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val viewModelStore = ViewModelStore()

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

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@CineVaultExternalPresentation)
            setViewTreeSavedStateRegistryOwner(this@CineVaultExternalPresentation)
            setViewTreeViewModelStoreOwner(this@CineVaultExternalPresentation)

            setContent {
                CineVaultTheme {
                    CompositionLocalProvider(
                        LocalCineVaultRenderDestination provides
                            CineVaultRenderDestination.EXTERNAL_DISPLAY
                    ) {
                        CineVaultSessionRoot {
                            HaloCanonicalCineVaultSurface {
                                CineVaultRoot()
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
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onStop()
    }

    override fun dismiss() {
        ExternalViewportSessionState.unbindProfile(display.name)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.dismiss()
    }
}
