package com.negotiation

import co.paralleluniverse.fibers.Suspendable
import com.negotiation.NegotiationState.Attributes
import com.negotiation.ProposalFlow.Role.Buyer
import com.negotiation.ProposalFlow.Role.Seller
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
import net.corda.core.utilities.unwrap

object ProposalFlow {
    enum class Role { Buyer, Seller;

        val opposite: Role
            get() = when (this) {
                Buyer -> Seller
                Seller -> Buyer
            }
    }

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        val id: String,
        val role: Role,
        val attributes: Attributes,
        val counterparty: Party
    ) : FlowLogic<UniqueIdentifier>() {
        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): UniqueIdentifier {

            // Creating the output.
            val (buyer, seller) = when (role) {
                Buyer -> ourIdentity to counterparty
                Seller -> counterparty to ourIdentity
            }
            val attributesHash = attributes.hashCode().toString(16)

            val dbService = serviceHub.cordaService(NegotiationDataBaseService::class.java)
            dbService.addProposal(id, role, attributes)

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
    class Initiator(val proposalId: UniqueIdentifier, val ourProposal: Attributes) :
        FlowLogic<UniqueIdentifier>() {
        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): UniqueIdentifier {
            // Retrieving the input from the vault.
            val inputCriteria =
                QueryCriteria.LinearStateQueryCriteria(linearId = listOf(proposalId))
            val inputStateAndRef =
                serviceHub.vaultService.queryBy<ProposalState>(inputCriteria).states.single()
            val input = inputStateAndRef.state.data

            // Creating the output.
            val attributesHash = Attributes(ourProposal.amount).hashCode().toString(16)
            val output = input.let {
                if (ourIdentity == it.buyer) it.copy(buyerAttributesHash = attributesHash)
                else it.copy(sellerAttributesHash = attributesHash)
            }

            // Creating the command.
            val requiredSigners = listOf(input.proposer.owningKey, input.proposee.owningKey)
            val command =
                Command(ProposalAndTradeContract.Commands.MatchProposal(), requiredSigners)

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
            val counterparty =
                if (ourIdentity == wellKnownProposer) wellKnownProposee else wellKnownProposer
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
data class BuyerSellerProposals(
    val buyerProposal: Attributes,
    val sellerProposal: Attributes
)

object RevealProposalsFlow {
    class Initiator(val proposalId: UniqueIdentifier, val ourProposal: Attributes) :
        FlowLogic<BuyerSellerProposals>() {
        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): BuyerSellerProposals {
            // Retrieving the input from the vault.
            val inputCriteria =
                QueryCriteria.LinearStateQueryCriteria(linearId = listOf(proposalId))
            val inputStateAndRef =
                serviceHub.vaultService.queryBy<ProposalState>(inputCriteria).states.single()
            val state = inputStateAndRef.state.data

            val (wellKnownProposer, wellKnownProposee) = listOf(
                state.proposer,
                state.proposee
            ).map { serviceHub.identityService.requireWellKnownPartyFromAnonymous(it) }
            val counterparty =
                if (ourIdentity == wellKnownProposer) wellKnownProposee else wellKnownProposer
            val counterpartySession = initiateFlow(counterparty)
            val theirProposal =
                counterpartySession.sendAndReceive<Attributes>(IdWithAttributes(proposalId, ourProposal))
                    .unwrap { their ->
                        val expectedHash =
                            if (counterparty == state.buyer) state.buyerAttributesHash else state.sellerAttributesHash
                        if (their.hashCode().toString(16) != expectedHash)
                            throw FlowException("The integrity of the proposal provided by the proposee cannot be guaranteed")
                        their
                    }
            val (buyerProposal, sellerProposal) = if (ourIdentity == state.buyer) ourProposal to theirProposal
            else theirProposal to ourProposal
            return BuyerSellerProposals(buyerProposal, sellerProposal)
        }
    }

    data class IdWithAttributes(val id: UniqueIdentifier, val attributes: Attributes)

    @InitiatedBy(Initiator::class)
    class Responder(val counterpartySession: FlowSession) :
        FlowLogic<Attributes>() {
        @Suspendable
        override fun call(): Attributes {
            val (proposalId, theirProposal) = counterpartySession.receive<IdWithAttributes>()
                .unwrap { (id, attr) ->

                    // Retrieving the current state from the vault.
                    val inputCriteria =
                        QueryCriteria.LinearStateQueryCriteria(linearId = listOf(id))
                    val state =
                        serviceHub.vaultService.queryBy<ProposalState>(inputCriteria).states.single()
                            .state.data

                    val expectedHash =
                        if (counterpartySession.counterparty == state.buyer) state.buyerAttributesHash else state.sellerAttributesHash

                    id to attr
                } // TODO: validate



            val dbService = serviceHub.cordaService(NegotiationDataBaseService::class.java)
            val ourRole = if (ourIdentity == state.buyer) Buyer else Seller

            dbService.addProposal(proposalId, ourRole.opposite, theirProposal)
            return dbService.queryProposal(proposalId, ourRole)
        }
    }
}

object ReconcileFlow {
    class Initiator(
        val buyerProposal: Attributes,
        val sellerProposal: Attributes
    ) : FlowLogic<UniqueIdentifier>() {

        override val progressTracker = ProgressTracker()

        override fun call(): UniqueIdentifier {
            val proposalId: UniqueIdentifier = UniqueIdentifier.fromString("kuku") // TODO

            // Retrieving the input from the vault.
            val inputCriteria =
                QueryCriteria.LinearStateQueryCriteria(linearId = listOf(proposalId))
            val inputStateAndRef =
                serviceHub.vaultService.queryBy<ProposalState>(inputCriteria).states.single()
            val input = inputStateAndRef.state.data

            val output = if (buyerProposal == sellerProposal) {
                TradeState(proposalId.externalId!!, input.buyer, input.seller, buyerProposal)
            } else {
                ProposalMismatchState(
                    proposalId.externalId!!,
                    input.buyer,
                    input.seller,
                    input.proposer,
                    input.proposee,
                    buyerProposal,
                    sellerProposal
                )
            }

            // Building the transaction.
            val notary = inputStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(inputStateAndRef)
            txBuilder.addOutputState(output, ProposalAndTradeContract.ID) // TODO: contract

            val (wellKnownProposer, wellKnownProposee) = listOf(
                input.proposer,
                input.proposee
            ).map { serviceHub.identityService.requireWellKnownPartyFromAnonymous(it) }
            val counterparty =
                if (ourIdentity == wellKnownProposer) wellKnownProposee else wellKnownProposer

            // Signing the transaction ourselves.
            val partStx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signature.
            val counterpartySession = initiateFlow(counterparty)
            val fullyStx = subFlow(CollectSignaturesFlow(partStx, listOf(counterpartySession)))

            // Finalising the transaction.
            val finalisedTx = subFlow(FinalityFlow(fullyStx))
            return finalisedTx.tx.outputsOfType<NegotiationState>().single().linearId
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        override fun call() {
            subFlow(object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                    val output = ledgerTx.outputsOfType<NegotiationState>().single()
                    val dbService = serviceHub.cordaService(NegotiationDataBaseService::class.java)
                    val buyerProposal = dbService.queryProposal(output.id, Buyer)
                    val sellerProposal = dbService.queryProposal(output.id, Seller)
                    when (output) {
                        is ProposalState -> throw FlowException()
                        is ProposalMismatchState -> if (buyerProposal == sellerProposal) throw FlowException(
                            "Transaction outcome is mismatch while proposals match"
                        )
                        is TradeState -> if (buyerProposal != sellerProposal) throw FlowException(
                            "Transaction outcome is match while proposals do not match"
                        )
                    }
                }
            })
        }
    }
}