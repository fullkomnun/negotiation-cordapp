package com.template.contracts

import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before

class ModificationContractTests {

    @Before
    fun setup() {
        setCordappPackages("com.template")
    }

    @After
    fun tearDown() {
        unsetCordappPackages()
    }

    // TODO: Tests not yet written. See ProposalContractTests.kt for examples.
}