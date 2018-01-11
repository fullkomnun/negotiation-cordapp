package com.template

import net.corda.core.node.services.queryBy
import net.corda.node.internal.StartedNode
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class FlowTests {
    private lateinit var network: MockNetwork
    private lateinit var a: StartedNode<MockNode>
    private lateinit var b: StartedNode<MockNode>

    @Before
    fun setup() {
        setCordappPackages("com.template")
        network = MockNetwork()
        val nodes = network.createSomeNodes(2)
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]

        val responseFlows = listOf(ProposalFlow.Responder::class.java, AcceptanceFlow.Responder::class.java, ModificationFlow.Responder::class.java)
        nodes.partyNodes.forEach {
            for (flow in responseFlows) {
                it.registerInitiatedFlow(flow)
            }
        }

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
        unsetCordappPackages()
    }

    @Test
    fun `proposal flow creates the correct proposals in both nodes' vaults when initiator is buyer`() {
        createProposal(ProposalFlow.Role.Buyer)
    }

    @Test
    fun `proposal flow creates the correct proposals in both nodes' vaults when initiator is seller`() {
        createProposal(ProposalFlow.Role.Seller)
    }

    fun createProposal(role: ProposalFlow.Role) {
        val amount = 1
        val counterparty = b.info.chooseIdentity()

        val flow = ProposalFlow.Initiator(role, amount, counterparty)
        val future = a.services.startFlow(flow).resultFuture
        network.runNetwork()
        future.get()

        for (node in listOf(a, b)) {
            node.database.transaction {
                val proposals = node.services.vaultService.queryBy<ProposalState>().states
                assertEquals(1, proposals.size)
                val proposal = proposals.single().state.data

                assertEquals(amount, proposal.amount)
                val (buyer, proposer, seller, proposee) = when (role) {
                    ProposalFlow.Role.Buyer -> listOf(a.info.chooseIdentity(), a.info.chooseIdentity(), b.info.chooseIdentity(), b.info.chooseIdentity())
                    ProposalFlow.Role.Seller -> listOf(b.info.chooseIdentity(), a.info.chooseIdentity(), a.info.chooseIdentity(), b.info.chooseIdentity())
                }

                assertEquals(buyer, proposal.buyer)
                assertEquals(proposer, proposal.proposer)
                assertEquals(seller, proposal.seller)
                assertEquals(proposee, proposal.proposee)
            }
        }
    }
}