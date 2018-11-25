package com.negotiation

import com.negotiation.NegotiationState.Attributes
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService

/**
 * A database service subclass for handling a table of proposals.
 *
 * @param services The node's service hub.
 */
@CordaService
class NegotiationDataBaseService(services: ServiceHub) : DatabaseService(services) {
    init {
        setUpStorage()
    }

    /**
     * Adds a proposal to the table of proposals.
     */
    fun addProposal(
        id: String,
        role: ProposalFlow.Role,
        attributes: Attributes
    ) {
        val query = "insert into $TABLE_NAME values(?, ?, ?)"

        val params = mapOf(1 to id, 2 to role.name, 3 to attributes.amount)

        executeUpdate(query, params)
        log.info("Proposal $id added to proposals table.")
    }

    /**
     * Updates the attributes of a proposal in the table of proposals.
     */
    fun updateProposal(
        id: String,
        role: ProposalFlow.Role,
        attributes: Attributes
    ) {
        val query = "update $TABLE_NAME set amount = ? where id = ? and role = ?"

        val params = mapOf(1 to attributes.amount, 2 to id, 3 to role.name)

        executeUpdate(query, params)
        log.info("Proposal $id updated in proposals table.")
    }

    /**
     * Retrieves the attributes of a proposal in the table of crypto values.
     */
    fun queryProposal(id: String, role: ProposalFlow.Role): Attributes {
        val query = "select * from $TABLE_NAME where id = ? and role = ?"

        val params = mapOf(1 to id, 2 to role.name)

        val results =
            executeQuery(query, params) { Attributes(amount = it.getBigDecimal("amount")) }

        if (results.isEmpty()) {
            throw IllegalArgumentException("Proposal $id not present in database.")
        }

        val amount = results.single()
        log.info("Proposal $id read from proposals table.")
        return amount
    }

    /**
     * Initialises the table of proposals.
     */
    private fun setUpStorage() {
        val query = """
            create table if not exists $TABLE_NAME(
                id varchar(64),
                role varchar(64),
                amount decimal
            )"""

        executeUpdate(query, emptyMap())
        log.info("Created proposals table.")
    }
}

private const val TABLE_NAME = "proposals"