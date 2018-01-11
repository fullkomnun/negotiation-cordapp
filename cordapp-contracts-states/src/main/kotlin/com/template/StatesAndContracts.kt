package com.template

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

open class ProposalAndTradeContract : Contract {
    companion object {
        val ID = "com.template.ProposalAndTradeContract"
    }

    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val cmd = tx.commands.requireSingleCommand<Commands>()
        when (cmd.value) {
            is Commands.Propose -> requireThat {
                // TODO: Add contractual logic.
            }
            is Commands.Accept -> requireThat {
                // TODO: Add contractual logic.
            }
            is Commands.Modify -> requireThat {
                // TODO: Add contractual logic.
            }
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class Propose : Commands
        class Accept : Commands
        class Modify : Commands
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