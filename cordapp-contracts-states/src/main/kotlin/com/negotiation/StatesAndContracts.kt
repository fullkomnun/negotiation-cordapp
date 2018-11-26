package com.negotiation

import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import java.math.BigDecimal

open class ProposalsContract : Contract {
    companion object {
        const val ID = "com.negotiation.ProposalsContract"
    }

    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val cmd = tx.commands.requireSingleCommand<Commands>()
        when (cmd.value) {
            is Commands.Propose -> requireThat {
                "There are no inputs" using (tx.inputStates.isEmpty())
                "There is exactly one output" using (tx.outputStates.size == 1)
                "The single output is of type ProposalState" using (tx.outputsOfType<ProposalState>().size == 1)
                "There is exactly one command" using (tx.commands.size == 1)
                "There is no timestamp" using (tx.timeWindow == null)

                val output = tx.outputsOfType<ProposalState>().single()
                "The buyer and seller are the proposer and the proposee" using (setOf(
                    output.buyer,
                    output.seller
                ) == setOf(output.proposer, output.proposee))

                "The proposer is a required signer" using (cmd.signers.contains(output.proposer.owningKey))
                "The proposee is a required signer" using (cmd.signers.contains(output.proposee.owningKey))
            }

            is Commands.MatchProposal -> requireThat {
                "There is exactly one input" using (tx.inputStates.size == 1)
                "The single input is of type ProposalState" using (tx.inputsOfType<ProposalState>().size == 1)
                "There is exactly one output" using (tx.outputStates.size == 1)
                "The single output is of type ProposalState" using (tx.outputsOfType<ProposalState>().size == 1)
                "There is exactly one command" using (tx.commands.size == 1)
                "There is no timestamp" using (tx.timeWindow == null)

                val input = tx.inputsOfType<ProposalState>().single()
                val output = tx.outputsOfType<ProposalState>().single()

                "The buyer is unmodified in the output" using (input.buyer == output.buyer)
                "The seller is unmodified in the output" using (input.seller == output.seller)
                "The buyer's attributes hash is either unmodified or initialized to a non-NULL value" using
                        (input.buyerAttributesHash == output.buyerAttributesHash || (input.buyerAttributesHash == null && output.buyerAttributesHash != null))
                "The seller's attributes hash is either unmodified or initialized to a non-NULL value" using
                        (input.sellerAttributesHash == output.sellerAttributesHash || (input.sellerAttributesHash == null && output.sellerAttributesHash != null))

                "The proposer is a required signer" using (cmd.signers.contains(input.proposer.owningKey))
                "The proposee is a required signer" using (cmd.signers.contains(input.proposee.owningKey))
            }
        }
    }

    // Used to indicate the transaction's intent.
    sealed class Commands : TypeOnlyCommandData() {
        class Propose : Commands()
        class MatchProposal : Commands()
    }
}

sealed class NegotiationState : LinearState {
    abstract val id: String

    data class Attributes(val amount: BigDecimal)
}

data class ProposalState(
    override val id: String,
    val buyer: AbstractParty,
    val seller: AbstractParty,
    val proposer: AbstractParty,
    val proposee: AbstractParty,
    val buyerAttributesHash: String? = null,
    val sellerAttributesHash: String? = null
) : NegotiationState() {
    override val linearId: UniqueIdentifier = UniqueIdentifier(externalId = id)
    override val participants = listOf(proposer, proposee)
}

data class ProposalMismatchState(
    override val id: String,
    val buyer: AbstractParty,
    val seller: AbstractParty,
    val proposer: AbstractParty,
    val proposee: AbstractParty,
    val buyerAttributesHash: Attributes,
    val sellerAttributesHash: Attributes
) : NegotiationState() {
    override val linearId: UniqueIdentifier = UniqueIdentifier(externalId = id)
    override val participants = listOf(proposer, proposee)
}

data class TradeState(
    override val id: String,
    val buyer: AbstractParty,
    val seller: AbstractParty,
    val attributes: Attributes
) : NegotiationState() {
    override val linearId: UniqueIdentifier = UniqueIdentifier(externalId = id)
    override val participants = listOf(buyer, seller)
}

open class ReconciliationContract : Contract {
    companion object {
        const val ID = "com.negotiation.ReconciliationContract"
    }

    override fun verify(tx: LedgerTransaction) {
        // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
        // and output states does not throw an exception.
        val output = tx.outputsOfType<NegotiationState>().single()
        when (output) {
            is ProposalState -> throw IllegalArgumentException()
            is ProposalMismatchState -> {
                "There is exactly one input" using (tx.inputStates.size == 1)
                "The single input is of type ProposalState" using (tx.inputsOfType<ProposalState>().size == 1)
                val input = tx.inputsOfType<ProposalState>().single()
                "The input state has non-null buyer attributes digest" using !input.buyerAttributesHash.isNullOrBlank()
                "The input state has non-null seller attributes digest" using !input.sellerAttributesHash.isNullOrBlank()
                "There is exactly one output" using (tx.outputStates.size == 1)
                "The single output is of type ProposalMismatchState" using (tx.outputsOfType<ProposalMismatchState>().size == 1)
                "There are no commands" using (tx.commands.isEmpty())
                "There is no timestamp" using (tx.timeWindow == null)
            }
            is TradeState -> {
                "There is exactly one input" using (tx.inputStates.size == 1)
                "The single input is of type ProposalState" using (tx.inputsOfType<ProposalState>().size == 1)
                val input = tx.inputsOfType<ProposalState>().single()
                "The input state has non-null buyer attributes digest" using !input.buyerAttributesHash.isNullOrBlank()
                "The input state has non-null seller attributes digest" using !input.sellerAttributesHash.isNullOrBlank()
                "There is exactly one output" using (tx.outputStates.size == 1)
                "The single output is of type TradeState" using (tx.outputsOfType<TradeState>().size == 1)
                "There are no commands" using (tx.commands.isEmpty())
                "There is no timestamp" using (tx.timeWindow == null)
            }
        }
    }
}