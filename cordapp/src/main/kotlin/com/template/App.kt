package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

// Flow pair 1
@InitiatingFlow
@StartableByRPC
class ProposeTrade(val amount: Int, val counterparty: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // TODO: Don't assume the initiator is always the buyer and the responder is always the seller.
        val output = ProposalState(amount, ourIdentity, counterparty, ourIdentity, counterparty)
        val requiredSigners = listOf(ourIdentity.owningKey, counterparty.owningKey)
        val command = Command(ProposalAndTradeContract.Commands.Propose(), requiredSigners)

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val txBuilder = TransactionBuilder(notary).addOutputState(output, ProposalAndTradeContract.ID).addCommand(command)
        val partStx = serviceHub.signInitialTransaction(txBuilder)

        val counterpartySession = initiateFlow(counterparty)
        val fullyStx = subFlow(CollectSignaturesFlow(partStx, listOf(counterpartySession)))
        subFlow(FinalityFlow(fullyStx))
    }
}

@InitiatedBy(ProposeTrade::class)
class ProposeTradeResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(object: SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                // TODO: Do some checking here.
            }
        })
    }
}

// Flow pair 2
@InitiatingFlow
@StartableByRPC
class AcceptProposal(val proposalId: UniqueIdentifier) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(proposalId))
        val inputStateAndRef = serviceHub.vaultService.queryBy<ProposalState>(inputCriteria).states.single()
        val input = inputStateAndRef.state.data
        val output = TradeState(input.amount, input.buyer, input.seller, input.linearId)
        val requiredSigners = listOf(input.proposer.owningKey, input.proposee.owningKey)
        val command = Command(ProposalAndTradeContract.Commands.Accept(), requiredSigners)

        val notary = inputStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary).addInputState(inputStateAndRef).addOutputState(output, ProposalAndTradeContract.ID).addCommand(command)
        val partStx = serviceHub.signInitialTransaction(txBuilder)

        val counterparty = serviceHub.identityService.requireWellKnownPartyFromAnonymous(input.proposer)
        val counterpartySession = initiateFlow(counterparty)
        val fullyStx = subFlow(CollectSignaturesFlow(partStx, listOf(counterpartySession)))
        subFlow(FinalityFlow(fullyStx))
    }
}

@InitiatedBy(ProposeTrade::class)
class AcceptProposalResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(object: SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                val proposee = ledgerTx.inputsOfType<ProposalState>().single().proposee
                if (proposee != counterpartySession.counterparty) {
                    throw FlowException("Only the proposee can accept a proposal.")
                }
            }
        })
    }
}

// Flow pair 3
@InitiatingFlow
@StartableByRPC
class ModifyProposal(val proposalId: UniqueIdentifier, val newAmount: Int) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(proposalId))
        val inputStateAndRef = serviceHub.vaultService.queryBy<ProposalState>(inputCriteria).states.single()
        val input = inputStateAndRef.state.data
        val counterparty = serviceHub.identityService.requireWellKnownPartyFromAnonymous(input.proposer)
        val output = input.copy(amount = newAmount, proposer = ourIdentity, proposee = counterparty)
        val requiredSigners = listOf(input.proposer.owningKey, input.proposee.owningKey)
        val command = Command(ProposalAndTradeContract.Commands.Modify(), requiredSigners)

        val notary = inputStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary).addInputState(inputStateAndRef).addOutputState(output, ProposalAndTradeContract.ID).addCommand(command)
        val partStx = serviceHub.signInitialTransaction(txBuilder)

        val counterpartySession = initiateFlow(counterparty)
        val fullyStx = subFlow(CollectSignaturesFlow(partStx, listOf(counterpartySession)))
        subFlow(FinalityFlow(fullyStx))
    }
}

@InitiatedBy(ProposeTrade::class)
class ModifyProposalResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(object: SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                val proposee = ledgerTx.inputsOfType<ProposalState>().single().proposee
                if (proposee != counterpartySession.counterparty) {
                    throw FlowException("Only the proposee can modify a proposal.")
                }
            }
        })
    }
}