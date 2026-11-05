package com.glicocalc.logic

import com.glicocalc.ui.matchesBaseFoodQuery
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoodSearchTest {
    @Test fun matchesFragmentsAnywhereAndInAnyOrder() {
        assertTrue(matchesSearchQuery("Făină de grâu", "rau"))
        assertTrue(matchesSearchQuery("Făină de grâu", "gra fai"))
        assertTrue(matchesSearchQuery("Făină de grâu", "  FAI   GRÂU  "))
        assertFalse(matchesSearchQuery("Făină de grâu", "fai orez"))
    }

    @Test fun searchesBothCanonicalAndTranslatedNames() {
        assertTrue(matchesBaseFoodQuery("Wheat Flour", "Făină de grâu", "gra"))
        assertTrue(matchesBaseFoodQuery("Wheat Flour", "Făină de grâu", "flour wheat"))
    }
}
