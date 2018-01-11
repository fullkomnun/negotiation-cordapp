package com.negotiation.flows

import com.negotiation.AcceptanceFlow
import com.negotiation.ModificationFlow
import com.negotiation.ProposalFlow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.node.internal.StartedNode
import net.corda.testing.node.MockNetwork
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before

abstract class FlowTestsBase {
    protected lateinit var network: MockNetwork
    protected lateinit var a: StartedNode<MockNetwork.MockNode>
    protected lateinit var b: StartedNode<MockNetwork.MockNode>

    @Before
    fun setup() {
        setCordappPackages("com.negotiation")
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

    fun nodeACreatesProposal(role: ProposalFlow.Role, amount: Int, counterparty: Party): UniqueIdentifier {
        val flow = ProposalFlow.Initiator(role, amount, counterparty)
        val future = a.services.startFlow(flow).resultFuture
        network.runNetwork()
        return future.get()
    }

    fun nodeBAcceptsProposal(proposalId: UniqueIdentifier) {
        val flow = AcceptanceFlow.Initiator(proposalId)
        val future = b.services.startFlow(flow).resultFuture
        network.runNetwork()
        future.get()

    }

    fun nodeBModifiesProposal(proposalId: UniqueIdentifier, newAmount: Int) {
        val flow = ModificationFlow.Initiator(proposalId, newAmount)
        val future = b.services.startFlow(flow).resultFuture
        network.runNetwork()
        future.get()
    }
}