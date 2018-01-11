package com.negotiation.contracts

import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before

class AcceptanceContractTests {

    @Before
    fun setup() {
        setCordappPackages("com.negotiation")
    }

    @After
    fun tearDown() {
        unsetCordappPackages()
    }

    // TODO: Tests not yet written. See ProposalContractTests.kt for examples.
}