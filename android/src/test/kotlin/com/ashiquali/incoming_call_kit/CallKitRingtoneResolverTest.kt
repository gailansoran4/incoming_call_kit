package com.ashiquali.incoming_call_kit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class CallKitRingtoneResolverTest {
    @Test
    fun emptyOrNullUsesSystemDefault() {
        assertNull(CallKitRingtoneResolver.rawResourceName(null))
        assertNull(CallKitRingtoneResolver.rawResourceName(""))
        assertNull(CallKitRingtoneResolver.rawResourceName("   "))
        assertTrue(CallKitRingtoneResolver.usesSystemDefault(null))
        assertTrue(CallKitRingtoneResolver.usesSystemDefault("system_ringtone_default"))
    }

    @Test
    fun systemAliasesUseSystemDefault() {
        assertNull(CallKitRingtoneResolver.rawResourceName("system_ringtone_default"))
        assertNull(CallKitRingtoneResolver.rawResourceName("DEFAULT"))
        assertNull(CallKitRingtoneResolver.rawResourceName("System"))
    }

    @Test
    fun customRawNameStripsExtension() {
        assertEquals("call_ring", CallKitRingtoneResolver.rawResourceName("call_ring"))
        assertEquals("call_ring", CallKitRingtoneResolver.rawResourceName("call_ring.mp3"))
        assertEquals("MyTone", CallKitRingtoneResolver.rawResourceName("MyTone.caf"))
    }
}
