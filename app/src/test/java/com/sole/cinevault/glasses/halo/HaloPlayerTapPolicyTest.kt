package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloPlayerTapPolicyTest {

    @Test
    fun hiddenControlsSingleTapShowsControls() {
        assertEquals(
            HaloPlayerTapRoute.SHOW_CONTROLS,
            HaloPlayerTapPolicy.route(
                controlsVisible = false,
                targetClicked = false,
            ),
        )
    }

    @Test
    fun visibleControlsTapOnHaloTargetClicksTarget() {
        assertEquals(
            HaloPlayerTapRoute.CLICK_TARGET,
            HaloPlayerTapPolicy.route(
                controlsVisible = true,
                targetClicked = true,
            ),
        )
    }

    @Test
    fun visibleControlsTapOnEmptySpaceHidesControls() {
        assertEquals(
            HaloPlayerTapRoute.HIDE_CONTROLS,
            HaloPlayerTapPolicy.route(
                controlsVisible = true,
                targetClicked = false,
            ),
        )
    }
}
