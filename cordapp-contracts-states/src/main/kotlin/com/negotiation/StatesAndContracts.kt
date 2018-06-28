package com.negotiation

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

open class ProposalAndTradeContract : Contract {
    companion object {
        val ID = "com.negotiation.ProposalAndTradeContract"
    }

    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val cmd = tx.commands.requireSingleCommand<Commands>()
        when (cmd.value) {
            is Commands.Propose -> requireThat {
                "There is exactly one output" using (tx.outputStates.size == 1)
                "The single output is of type ProposalState" using (tx.outputsOfType<ProposalState>().size == 1)
                val output = tx.outputsOfType<ProposalState>().single()

                "There is exactly one command" using (tx.commands.size == 1)

                "The proposer is a required signer" using (cmd.signers.contains(output.proposer.owningKey))
                "The proposee is a required signer" using (cmd.signers.contains(output.proposee.owningKey))

                "The buyer is either the proposer or the proposee" using (output.buyer in listOf(output.proposer, output.proposee))
                "The seller is either the proposer or the proposee" using (output.seller in listOf(output.proposer, output.proposee))

                "There are zero input states" using (tx.inputStates.isEmpty())
                "There is no timestamp" using (tx.timeWindow == null)
            }

            is Commands.Accept -> requireThat {
                val input = tx.inputsOfType<ProposalState>().single()
                val output = tx.outputsOfType<TradeState>().single()

                "There is exactly one output" using (tx.outputStates.size == 1)
                "There is exactly one input" using (tx.inputStates.size == 1)

                "The amount should be same after accepting the proposal" using (output.amount == input.amount)

                "The buyer and seller are unmodified in the output" using (input.buyer == output.buyer && input.seller == output.seller)

                "The proposer is a required signer" using (cmd.signers.contains(input.proposer.owningKey))
                "The proposee is a required signer" using (cmd.signers.contains(input.proposee.owningKey))
            }

            is Commands.Modify -> requireThat {
                val output = tx.outputsOfType<ProposalState>().single()
                val input = tx.inputsOfType<ProposalState>().single()

                "There is exactly one output" using (tx.outputStates.size == 1)
                "There is exactly one input" using (tx.inputStates.size == 1)
                "The new amount and old amount should not be same" using (output.amount != input.amount)

                "The proposer is a required signer in the input" using (cmd.signers.contains(input.proposer.owningKey))
                "The proposee is a required signer in the input" using (cmd.signers.contains(input.proposee.owningKey))

                "The proposer is a required signer in the output" using (cmd.signers.contains(output.proposer.owningKey))
                "The proposee is a required signer in the output" using (cmd.signers.contains(output.proposee.owningKey))

                "The buyer and seller are unmodified in the output" using (input.buyer == output.buyer && input.seller == output.seller)
            }
        }
    }

    // Used to indicate the transaction's intent.
    sealed class Commands : TypeOnlyCommandData() {
        class Propose : Commands()
        class Accept : Commands()
        class Modify : Commands()
    }
}

data class ProposalState(
        val amount: Int,
        val buyer: AbstractParty,
        val seller: AbstractParty,
        val proposer: AbstractParty,
        val proposee: AbstractParty,
        override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {
    override val participants = listOf(proposer, proposee)
}

data class TradeState(
        val amount: Int,
        val buyer: AbstractParty,
        val seller: AbstractParty,
        override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {
    override val participants = listOf(buyer, seller)
}