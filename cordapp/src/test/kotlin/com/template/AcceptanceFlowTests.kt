package com.template

import net.corda.core.node.services.queryBy
import net.corda.testing.chooseIdentity
import org.junit.Test
import kotlin.test.assertEquals

class AcceptanceFlowTests: FlowTestsBase() {
    @Test
    fun `acceptance flow consumes the proposals in both nodes' vaults and replaces them with equivalent accepted trades when initiator is buyer`() {
        testAcceptanceForRole(ProposalFlow.Role.Seller)
    }

    @Test
    fun `acceptance flow consumes the proposals in both nodes' vaults and replaces them with equivalent accepted trades when initiator is seller`() {
        testAcceptanceForRole(ProposalFlow.Role.Buyer)
    }

    fun testAcceptanceForRole(role: ProposalFlow.Role) {
        val amount = 1
        val counterparty = b.info.chooseIdentity()

        val proposalId = nodeACreatesProposal(role, amount, counterparty)

        nodeBAcceptsProposal(proposalId)

        for (node in listOf(a, b)) {
            node.database.transaction {
                val proposals = node.services.vaultService.queryBy<ProposalState>().states
                assertEquals(0, proposals.size)

                val trades = node.services.vaultService.queryBy<TradeState>().states
                assertEquals(1, trades.size)
                val trade = trades.single().state.data

                assertEquals(amount, trade.amount)
                val (buyer, seller) = when (role) {
                    ProposalFlow.Role.Buyer -> listOf(a.info.chooseIdentity(), b.info.chooseIdentity())
                    ProposalFlow.Role.Seller -> listOf(b.info.chooseIdentity(), a.info.chooseIdentity())
                }

                assertEquals(buyer, trade.buyer)
                assertEquals(seller, trade.seller)
            }
        }
    }
}