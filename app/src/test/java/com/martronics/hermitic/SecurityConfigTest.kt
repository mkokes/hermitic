package com.martronics.hermitic

import com.martronics.hermitic.ui.navigation.HermiticRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityConfigTest {
    @Test
    fun routesAreDefined() {
        assertEquals("onboarding", HermiticRoutes.ONBOARDING)
        assertEquals("sessions", HermiticRoutes.SESSIONS)
        assertTrue(HermiticRoutes.CHAT.contains("{sessionId}"))
        assertEquals("settings", HermiticRoutes.SETTINGS)
    }
}
