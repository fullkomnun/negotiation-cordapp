package com.negotiation

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.math.BigDecimal

object ProposalFlow {
    enum class Role { Buyer, Seller }

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        val id: String,
        val role: Role,
        val attributes: NegotiationState.Attributes,
        val counterparty: Party
    ) : FlowLogic<UniqueIdentifier>() {
        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): UniqueIdentifier {

            // Creating the output.
            val (buyer, seller) = when (role) {
                Role.Buyer -> ourIdentity to counterparty
                Role.Seller -> counterparty to ourIdentity
            }
            val attributesHash = attributes.hashCode().toString(16)

            val dbService = serviceHub.cordaService(NegotiationDataBaseService::class.java)
            dbService.addProposal(id, attributes.amount)

            val output = ProposalState(id, buyer, seller, ourIdentity, counterparty)
                .let {
                    if (ourIdentity == buyer) it.copy(buyerAttributesHash = attributesHash)
                    else it.copy(sellerAttributesHash = attributesHash)
                }

            // Creating the command.
            val commandType = ProposalAndTradeContract.Commands.Propose()
            val requiredSigners = listOf(ourIdentity.owningKey, counterparty.owningKey)
            val command = Command(commandType, requiredSigners)

            // Building the transaction.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addOutputState(output, ProposalAndTradeContract.ID)
            txBuilder.addCommand(command)

            // Signing the transaction ourselves.
            val partStx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signature.
            val counterpartySession = initiateFlow(counterparty)
            val fullyStx = subFlow(CollectSignaturesFlow(partStx, listOf(counterpartySession)))

            // Finalising the transaction.
            val finalisedTx = subFlow(FinalityFlow(fullyStx))
            return finalisedTx.tx.outputsOfType<ProposalState>().single().linearId
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    // No checking to be done.
                }
            })
        }
    }
}

object MatchProposalFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val proposalId: UniqueIdentifier, val amount: BigDecimal) : FlowLogic<Unit>() {
        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call() {
            // Retrieving the input from the vault.
            val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(proposalId))
            val inputStateAndRef = serviceHub.vaultService.queryBy<ProposalState>(inputCriteria).states.single()
            val input = inputStateAndRef.state.data

            val dbService = serviceHub.cordaService(NegotiationDataBaseService::class.java)
            dbService.addProposal(proposalId.externalId!!, amount)

            // Creating the output.
            val attributesHash = NegotiationState.Attributes(amount).hashCode().toString(16)
            val output = input.let {
                if (ourIdentity == it.buyer) it.copy(buyerAttributesHash = attributesHash)
                else it.copy(sellerAttributesHash = attributesHash)
            }

            // Creating the command.
            val requiredSigners = listOf(input.proposer.owningKey, input.proposee.owningKey)
            val command = Command(ProposalAndTradeContract.Commands.MatchProposal(), requiredSigners)

            // Building the transaction.
            val notary = inputStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(inputStateAndRef)
            txBuilder.addOutputState(output, ProposalAndTradeContract.ID)
            txBuilder.addCommand(command)

            // Signing the transaction ourselves.
            val partStx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signature.
            val (wellKnownProposer, wellKnownProposee) = listOf(
                input.proposer,
                input.proposee
            ).map { serviceHub.identityService.requireWellKnownPartyFromAnonymous(it) }
            val counterparty = if (ourIdentity == wellKnownProposer) wellKnownProposee else wellKnownProposer
            val counterpartySession = initiateFlow(counterparty)
            val fullyStx = subFlow(CollectSignaturesFlow(partStx, listOf(counterpartySession)))

            // Finalising the transaction.
            subFlow(FinalityFlow(fullyStx))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(object : SignTransactionFlow(counterpartySession) {
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
}

@CordaSerializable
data class BuyerSellerProposals(val buyerProposal: NegotiationState.Attributes, val sellerProposals: BuyerSellerProposals)

object RevealProposalsFlow {
    class Initiator(val proposalId: UniqueIdentifier) : FlowLogic<BuyerSellerProposals>() {
        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): BuyerSellerProposals {
            // Retrieving the input from the vault.
            val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(proposalId))
            val inputStateAndRef = serviceHub.vaultService.queryBy<ProposalState>(inputCriteria).states.single()
            val input = inputStateAndRef.state.data

            // Creating the output.
            val attributesHash = NegotiationState.Attributes(amount).hashCode().toString(16)
            val output = input.let {
                if (ourIdentity == it.buyer) it.copy(buyerAttributesHash = attributesHash)
                else it.copy(sellerAttributesHash = attributesHash)
            }

            // Creating the command.
            val requiredSigners = listOf(input.proposer.owningKey, input.proposee.owningKey)
            val command = Command(ProposalAndTradeContract.Commands.MatchProposal(), requiredSigners)

            // Building the transaction.
            val notary = inputStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(inputStateAndRef)
            txBuilder.addOutputState(output, ProposalAndTradeContract.ID)
            txBuilder.addCommand(command)

            // Signing the transaction ourselves.
            val partStx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signature.
            val (wellKnownProposer, wellKnownProposee) = listOf(
                input.proposer,
                input.proposee
            ).map { serviceHub.identityService.requireWellKnownPartyFromAnonymous(it) }
            val counterparty = if (ourIdentity == wellKnownProposer) wellKnownProposee else wellKnownProposer
            val counterpartySession = initiateFlow(counterparty)
            val fullyStx = subFlow(CollectSignaturesFlow(partStx, listOf(counterpartySession)))

            // Finalising the transaction.
            subFlow(FinalityFlow(fullyStx))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val counterpartySession: FlowSession) : FlowLogic<NegotiationState.Attributes>() {
        @Suspendable
        override fun call() {
            subFlow(object : SignTransactionFlow(counterpartySession) {
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
}

//object AcceptanceFlow {
//    @InitiatingFlow
//    @StartableByRPC
//    class Initiator(val proposalId: UniqueIdentifier) : FlowLogic<Unit>() {
//        override val progressTracker = ProgressTracker()
//
//        @Suspendable
//        override fun call() {
//            // Retrieving the input from the vault.
//            val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(proposalId))
//            val inputStateAndRef = serviceHub.vaultService.queryBy<ProposalState>(inputCriteria).states.single()
//            val input = inputStateAndRef.state.data
//
//            // Creating the output.
//            val output = TradeState(input.amount, input.buyer, input.seller, input.linearId)
//
//            // Creating the command.
//            val requiredSigners = listOf(input.proposer.owningKey, input.proposee.owningKey)
//            val command = Command(ProposalAndTradeContract.Commands.Accept(), requiredSigners)
//
//            // Building the transaction.
//            val notary = inputStateAndRef.state.notary
//            val txBuilder = TransactionBuilder(notary)
//            txBuilder.addInputState(inputStateAndRef)
//            txBuilder.addOutputState(output, ProposalAndTradeContract.ID)
//            txBuilder.addCommand(command)
//
//            // Signing the transaction ourselves.
//            val partStx = serviceHub.signInitialTransaction(txBuilder)
//
//            // Gathering the counterparty's signature.
//            val (wellKnownProposer, wellKnownProposee) = listOf(input.proposer, input.proposee).map { serviceHub.identityService.requireWellKnownPartyFromAnonymous(it) }
//            val counterparty = if (ourIdentity == wellKnownProposer) wellKnownProposee else wellKnownProposer
//            val counterpartySession = initiateFlow(counterparty)
//            val fullyStx = subFlow(CollectSignaturesFlow(partStx, listOf(counterpartySession)))
//
//            // Finalising the transaction.
//            subFlow(FinalityFlow(fullyStx))
//        }
//    }
//
//    @InitiatedBy(Initiator::class)
//    class Responder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
//        @Suspendable
//        override fun call() {
//            subFlow(object : SignTransactionFlow(counterpartySession) {
//                override fun checkTransaction(stx: SignedTransaction) {
//                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
//                    val proposee = ledgerTx.inputsOfType<ProposalState>().single().proposee
//                    if (proposee != counterpartySession.counterparty) {
//                        throw FlowException("Only the proposee can accept a proposal.")
//                    }
//                }
//            })
//        }
//    }
//}

//object ModificationFlow {
//    @InitiatingFlow
//    @StartableByRPC
//    class Initiator(val proposalId: UniqueIdentifier, val newAmount: Int) : FlowLogic<Unit>() {
//        override val progressTracker = ProgressTracker()
//
//        @Suspendable
//        override fun call() {
//            // Retrieving the input from the vault.
//            val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(proposalId))
//            val inputStateAndRef = serviceHub.vaultService.queryBy<ProposalState>(inputCriteria).states.single()
//            val input = inputStateAndRef.state.data
//
//            // Creating the output.
//            val (wellKnownProposer, wellKnownProposee) = listOf(input.proposer, input.proposee).map { serviceHub.identityService.requireWellKnownPartyFromAnonymous(it) }
//            val counterparty = if (ourIdentity == wellKnownProposer) wellKnownProposee else wellKnownProposer
//            val output = input.copy(amount = newAmount, proposer = ourIdentity, proposee = counterparty)
//
//            // Creating the command.
//            val requiredSigners = listOf(input.proposer.owningKey, input.proposee.owningKey)
//            val command = Command(ProposalAndTradeContract.Commands.Modify(), requiredSigners)
//
//            // Building the transaction.
//            val notary = inputStateAndRef.state.notary
//            val txBuilder = TransactionBuilder(notary)
//            txBuilder.addInputState(inputStateAndRef)
//            txBuilder.addOutputState(output, ProposalAndTradeContract.ID)
//            txBuilder.addCommand(command)
//
//            // Signing the transaction ourselves.
//            val partStx = serviceHub.signInitialTransaction(txBuilder)
//
//            // Gathering the counterparty's signature.
//            val counterpartySession = initiateFlow(counterparty)
//            val fullyStx = subFlow(CollectSignaturesFlow(partStx, listOf(counterpartySession)))
//
//            // Finalising the transaction.
//            subFlow(FinalityFlow(fullyStx))
//        }
//    }

//    @InitiatedBy(Initiator::class)
//    class Responder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
//        @Suspendable
//        override fun call() {
//            subFlow(object : SignTransactionFlow(counterpartySession) {
//                override fun checkTransaction(stx: SignedTransaction) {
//                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
//                    val proposee = ledgerTx.inputsOfType<ProposalState>().single().proposee
//                    if (proposee != counterpartySession.counterparty) {
//                        throw FlowException("Only the proposee can modify a proposal.")
//                    }
//                }
//            })
//        }
//    }
//}