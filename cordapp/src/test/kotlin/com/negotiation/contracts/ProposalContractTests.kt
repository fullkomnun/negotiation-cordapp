package com.negotiation.contracts

import com.negotiation.ProposalAndTradeContract
import com.negotiation.ProposalState
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
        setCordappPackages("com.negotiation")
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
                    output(ProposalAndTradeContract.ID) { DummyState() }
                    fails()
                }
                tweak {
                    output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, BOB) }
                    output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, BOB) }
                    fails()
                }
                output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, BOB) }
                verifies()
            }
        }
    }

    @Test
    fun `proposal transactions have exactly one command of type Propose`() {
        ledger {
            transaction {
                output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, BOB) }
                tweak {
                    command(ALICE_PUBKEY, BOB_PUBKEY) { DummyCommandData }
                    fails()
                }
                tweak {
                    command(ALICE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                    command(ALICE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                    fails()
                }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                verifies()
            }
        }
    }

    @Test
    fun `proposal transactions have two required signers - the proposer and the proposee`() {
        ledger {
            transaction {
                output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, BOB) }
                tweak {
                    command(ALICE_PUBKEY, CHARLIE_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                    fails()
                }
                tweak {
                    command(CHARLIE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                    fails()
                }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                verifies()
            }
        }
    }

    @Test
    fun `in proposal transactions, the proposer and proposee are the buyer and seller`() {
        ledger {
            transaction {
                command(ALICE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                tweak {
                    // Order reversed - buyer = proposee, seller = proposer
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
                output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, BOB) }
                verifies()
            }
        }
    }

    @Test
    fun `proposal transactions have no inputs and no timestamp`() {
        ledger {
            transaction {
                output(ProposalAndTradeContract.ID) { ProposalState(1, ALICE, BOB, ALICE, BOB) }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ProposalAndTradeContract.Commands.Propose() }
                tweak {
                    input(DUMMY_PROGRAM_ID) { DummyState() }
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
