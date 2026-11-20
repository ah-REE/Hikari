package com.hikari.app

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class SponsorSegmentsTest {
    @Test fun malformedOrReversedSegmentsAreIgnored() {
        val parsed = SponsorSegments.parse(JSONArray("""[{"segment":[20,30]},{"segment":[-1,2]},{"segment":[4,3]},{"segment":[5,10]},{"segment":[]}]"""))
        assertEquals(listOf(SponsorSegment(5f,10f), SponsorSegment(20f,30f)), parsed)
    }
    @Test fun endBoundaryDoesNotSkipAgain() {
        val segment = SponsorSegment(5f,10f)
        assertFalse(segment.contains(4.99f)); assertTrue(segment.contains(5f)); assertFalse(segment.contains(10f))
    }
}
