package com.template.contracts

import com.template.ProposalAndTradeContract
import com.template.ProposalState
import net.corda.core.crypto.SecureHash
import net.corda.testing.*
import net.corda.testing.contracts.DUMMY_PROGRAM_ID
import net.corda.testing.contracts.DummyState
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ProposalContractTests {

    @Before
    fun setup() {
        setCordappPackages("com.template")
    }

    @After
    fun tearDown() {
        unsetCordappPackages()
    }

    @Test
    fun `proposal transactions have exactly one output of type ProposalState`() {
        ledger {
            transaction {
                command(ALICE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                fails()
                tweak {
                    output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, BOB) }
                    verifies()
                }
                tweak {
                    output(DUMMY_PROGRAM_ID) { DummyState() }
                    fails()
                }
                tweak {
                    output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, BOB) }
                    output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, BOB) }
                    fails()
                }
            }
        }
    }

    @Test
    fun `proposal transactions have exactly one command of type Propose`() {
        ledger {
            transaction {
                output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, BOB) }
                fails()
                tweak {
                    command(ALICE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                    verifies()
                }
                tweak {
                    command(ALICE_PUBKEY, BOB_PUBKEY) { DummyCommandData }
                    fails()
                }
                tweak {
                    command(ALICE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                    command(ALICE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                    fails()
                }
            }
        }
    }

    @Test
    fun `proposal transactions have two required signers - the proposer and the proposee`() {
        ledger {
            transaction {
                output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, BOB) }
                tweak {
                    command(ALICE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                    verifies()
                }
                tweak {
                    command(ALICE_PUBKEY, CHARLIE_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                    fails()
                }
                tweak {
                    command(CHARLIE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                    fails()
                }
            }
        }
    }

    @Test
    fun `in proposal transactions, the proposer and proposee are distinct`() {
        ledger {
            transaction {
                command(ALICE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                tweak {
                    output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, BOB) }
                    verifies()
                }
                tweak {
                    output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, ALICE) }
                    fails()
                }
            }
        }
    }

    @Test
    fun `in proposal transactions, the proposer and proposee are the buyer and seller`() {
        ledger {
            transaction {
                command(ALICE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                tweak {
                    output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, BOB) }
                    verifies()
                }
                tweak {
                    output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, BOB, ALICE) }
                    verifies()
                }
                tweak {
                    output(ProposalAndTradeContract.ID) { ProposalState(1, CHARLIE, BOB, ALICE, BOB) }
                    fails()
                }
                tweak {
                    output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, CHARLIE, ALICE, BOB) }
                    fails()
                }
            }
        }
    }

    @Test
    fun `proposal transactions have no inputs, attachments or timestamps`() {
        ledger {
            transaction {
                output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, BOB) }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                tweak {
                    input(DUMMY_PROGRAM_ID) { DummyState() }
                    fails()
                }
                tweak {
                    attachment(SecureHash.zeroHash)
                    fails()
                }
                tweak {
                    timeWindow(Instant.now())
                    fails()
                }
            }
        }
    }
}