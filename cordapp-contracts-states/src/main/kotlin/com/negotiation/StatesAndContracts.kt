package com.negotiation

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import java.math.BigDecimal

open class ProposalAndTradeContract : Contract {
    companion object {
        const val ID = "com.negotiation.ProposalAndTradeContract"
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
                "The single output is of type TradeState" using (tx.outputsOfType<ProposalState>().size == 1)
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

            is Commands.Modify -> requireThat {
                "There is exactly one input" using (tx.inputStates.size == 1)
                "The single input is of type ProposalState" using (tx.inputsOfType<ProposalState>().size == 1)
                "There is exactly one output" using (tx.outputStates.size == 1)
                "The single output is of type ProposalState" using (tx.outputsOfType<ProposalState>().size == 1)
                "There is exactly one command" using (tx.commands.size == 1)
                "There is no timestamp" using (tx.timeWindow == null)

                val output = tx.outputsOfType<ProposalState>().single()
                val input = tx.inputsOfType<ProposalState>().single()

                "The amount is modified in the output" using (output.amount != input.amount)
                "The buyer is unmodified in the output" using (input.buyer == output.buyer)
                "The seller is unmodified in the output" using (input.seller == output.seller)

                "The proposer is a required signer" using (cmd.signers.contains(output.proposer.owningKey))
                "The proposee is a required signer" using (cmd.signers.contains(output.proposee.owningKey))
            }
        }
    }

    // Used to indicate the transaction's intent.
    sealed class Commands : TypeOnlyCommandData() {
        class Propose : Commands()
        class MatchProposal : Commands()
        class Modify : Commands()
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