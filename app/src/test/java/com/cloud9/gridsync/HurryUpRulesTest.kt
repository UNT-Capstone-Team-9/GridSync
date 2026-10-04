package com.cloud9.gridsync

import com.cloud9.gridsync.database.HurryUpRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HurryUpRulesTest {

    @Test
    fun blankPackageNameIsRejected() {
        assertNotNull(HurryUpRepository.validatePackageName("   ", nameTaken = false))
    }

    @Test
    fun takenPackageNameIsRejected() {
        assertNotNull(HurryUpRepository.validatePackageName("Redzone", nameTaken = true))
    }

    @Test
    fun anyCustomNameIsAccepted() {
        assertNull(HurryUpRepository.validatePackageName("Coach K Special #3", nameTaken = false))
    }

    @Test
    fun packageNeedsAtLeastTwoPlays() {
        assertEquals(HurryUpRepository.MIN_PLAYS_MESSAGE, HurryUpRepository.validatePlaySelection(1))
        assertNull(HurryUpRepository.validatePlaySelection(2))
    }
}
