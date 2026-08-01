package com.sole.cinevault.ui.theme

import androidx.compose.ui.unit.dp

// Spacing tokens for screens/components going forward. Deliberately NOT
// retrofitted onto existing screens — Detail/Library/Search/Settings each
// use slightly different padding/spacing today (22dp/16dp/16dp/20dp), and
// that variation largely reflects real differences in content density
// between those screens rather than carelessness. Rewriting every existing
// value to match one of these five tokens would be a purely cosmetic
// change touching a huge number of call sites for no visible improvement,
// with real risk of regressing spacing that was actually tuned on-device.
// Use these for new screens/components; leave working screens alone.
object CineDimens {
    val screenPadding = 16.dp
    val cardSpacing = 12.dp
    val sectionSpacing = 18.dp
    val componentGap = 8.dp
    val smallGap = 4.dp
}
