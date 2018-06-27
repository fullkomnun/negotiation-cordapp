package com.negotiation.contracts

import com.negotiation.ProposalAndTradeContract
import com.negotiation.ProposalState
import com.negotiation.TradeState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class AcceptanceContractTests {

    private val ledgerServices = MockServices(listOf("com.negotiation", "net.corda.testing.contracts"))
    private val alice = TestIdentity(CordaX500Name("alice", "New York", "US"))
    private val bob = TestIdentity(CordaX500Name("bob", "Tokyo", "JP"))
    private val charlie = TestIdentity(CordaX500Name("charlie", "London", "GB"))

    @Test
    fun `proposal and trade should have exactly same ammounts`() {
        ledgerServices.ledger{
            transaction {
                command(listOf(alice.publicKey, bob.publicKey), ProposalAndTradeContract.Commands.Accept())
                tweak {

                    input(ProposalAndTradeContract.ID, ProposalState(1, alice.party, bob.party, alice.party, bob.party))
                    output(ProposalAndTradeContract.ID,TradeState(2,alice.party,bob.party))
                    fails()
                }
                tweak {

                    input(ProposalAndTradeContract.ID, ProposalState(2, alice.party, bob.party, alice.party, bob.party))
                    output(ProposalAndTradeContract.ID,TradeState(1,alice.party,bob.party))
                    fails()
                }
                input(ProposalAndTradeContract.ID, ProposalState(1, alice.party, bob.party, alice.party, bob.party))
                output(ProposalAndTradeContract.ID,TradeState(1,alice.party,bob.party))
                verifies()
            }
        }
    }

    @Test
    fun `transactions have only one input proposalState and one output TradeState`() {
        ledgerServices.ledger{
            transaction {

                command(listOf(alice.publicKey, bob.publicKey), ProposalAndTradeContract.Commands.Accept())
                tweak {
                    input(ProposalAndTradeContract.ID,ProposalState(1, alice.party, bob.party, alice.party, bob.party))
                    input(ProposalAndTradeContract.ID,ProposalState(1, alice.party, bob.party, alice.party, bob.party))
                    fails()
                }
                tweak {

                    output(ProposalAndTradeContract.ID, TradeState(1,alice.party,bob.party))
                    output(ProposalAndTradeContract.ID, TradeState(1,alice.party,bob.party))
                    fails()
                }
                input(ProposalAndTradeContract.ID,ProposalState(1, alice.party, bob.party, alice.party, bob.party))
                output(ProposalAndTradeContract.ID, TradeState(1,alice.party,bob.party))
                verifies()
            }
        }
    }



    @Test
    fun `transactions have two required signers - the proposer and the proposee`() {
        ledgerServices.ledger {
            transaction {
                input(ProposalAndTradeContract.ID,ProposalState(1, alice.party, bob.party, alice.party, bob.party))
                output(ProposalAndTradeContract.ID, TradeState(1,alice.party,bob.party))
                tweak {
                    command(listOf(alice.publicKey, charlie.publicKey), ProposalAndTradeContract.Commands.Accept())
                    fails()
                }
                tweak {
                    command(listOf(charlie.publicKey, bob.publicKey), ProposalAndTradeContract.Commands.Accept())
                    fails()
                }
                command(listOf(alice.publicKey, bob.publicKey), ProposalAndTradeContract.Commands.Accept())
                verifies()
            }
        }
    }

}