package com.negotiation.contracts

import com.negotiation.ProposalAndTradeContract
import com.negotiation.ProposalState
import com.negotiation.TradeState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class ModificationContractTests {
    private val ledgerServices = MockServices(listOf("com.negotiation", "net.corda.testing.contracts"))
    private val alice = TestIdentity(CordaX500Name("alice", "New York", "US"))
    private val bob = TestIdentity(CordaX500Name("bob", "Tokyo", "JP"))
    private val charlie = TestIdentity(CordaX500Name("charlie", "London", "GB"))

    @Test
    fun `proposal transactions have exactly different amounts`() {
        ledgerServices.ledger{
            transaction {

                command(listOf(alice.publicKey, bob.publicKey), ProposalAndTradeContract.Commands.Modify())
                tweak {
                    input(ProposalAndTradeContract.ID,ProposalState(1, alice.party, bob.party, alice.party, bob.party))
                    output(ProposalAndTradeContract.ID, ProposalState(1, alice.party, bob.party, alice.party, bob.party))
                    fails()
                }
                input(ProposalAndTradeContract.ID,ProposalState(1, alice.party, bob.party, alice.party, bob.party))
                output(ProposalAndTradeContract.ID, ProposalState(2, alice.party, bob.party, alice.party, bob.party))
                verifies()
            }
        }
    }

    @Test
    fun `proposal transactions have only one output input and output`() {
        ledgerServices.ledger{
            transaction {

                command(listOf(alice.publicKey, bob.publicKey), ProposalAndTradeContract.Commands.Modify())
                tweak {
                    input(ProposalAndTradeContract.ID,ProposalState(1, alice.party, bob.party, alice.party, bob.party))
                    input(ProposalAndTradeContract.ID,ProposalState(1, alice.party, bob.party, alice.party, bob.party))
                    fails()
                }
                tweak {

                    output(ProposalAndTradeContract.ID, ProposalState(2, alice.party, bob.party, alice.party, bob.party))
                    output(ProposalAndTradeContract.ID, ProposalState(2, alice.party, bob.party, alice.party, bob.party))
                    fails()
                }
                input(ProposalAndTradeContract.ID,ProposalState(1, alice.party, bob.party, alice.party, bob.party))
                output(ProposalAndTradeContract.ID, ProposalState(2, alice.party, bob.party, alice.party, bob.party))
                verifies()
            }
        }
    }

    @Test
    fun `proposal transaction's input and output should be of type ProposalState`() {
        ledgerServices.ledger{
            transaction {
                input(ProposalAndTradeContract.ID,ProposalState(1, alice.party, bob.party, alice.party, bob.party))
                output(ProposalAndTradeContract.ID, ProposalState(2, alice.party, bob.party, alice.party, bob.party))
                command(listOf(alice.publicKey, bob.publicKey), ProposalAndTradeContract.Commands.Modify())
                tweak {
                    input(ProposalAndTradeContract.ID,DummyState())
                    fails()
                }
                tweak {
                    output(ProposalAndTradeContract.ID, DummyState())
                    fails()
                }
               verifies()
            }
        }
    }

    @Test
    fun `proposal transactions have two required signers - the proposer and the proposee`() {
        ledgerServices.ledger {
            transaction {
                input(ProposalAndTradeContract.ID,ProposalState(1, alice.party, bob.party, alice.party, bob.party))
                output(ProposalAndTradeContract.ID, ProposalState(2, alice.party, bob.party, alice.party, bob.party))
                tweak {
                    command(listOf(alice.publicKey, charlie.publicKey), ProposalAndTradeContract.Commands.Modify())
                    fails()
                }
                tweak {
                    command(listOf(charlie.publicKey, bob.publicKey), ProposalAndTradeContract.Commands.Modify())
                    fails()
                }
                command(listOf(alice.publicKey, bob.publicKey), ProposalAndTradeContract.Commands.Modify())
                verifies()
            }
        }
    }

    @Test
    fun `The buyer and seller are unmodified in the output`() {
        ledgerServices.ledger {
            transaction {
                input(ProposalAndTradeContract.ID,ProposalState(1, alice.party, bob.party, alice.party, bob.party))
                output(ProposalAndTradeContract.ID, TradeState(1,alice.party,bob.party))
                command(listOf(alice.publicKey, bob.publicKey), ProposalAndTradeContract.Commands.Accept())
                tweak {
                    output(ProposalAndTradeContract.ID, TradeState(1,alice.party, charlie.party))
                    fails()
                }
                tweak {
                    output(ProposalAndTradeContract.ID, TradeState(1,charlie.party, bob.party))
                    fails()
                }
                tweak {
                    output(ProposalAndTradeContract.ID, TradeState(1,bob.party, bob.party))
                    fails()
                }
                verifies()
            }
        }
    }
}