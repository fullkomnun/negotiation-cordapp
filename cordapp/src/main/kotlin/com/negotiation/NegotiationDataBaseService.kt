package com.negotiation

import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import java.math.BigDecimal

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
    fun addProposal(id: String, amount: BigDecimal) {
        val query = "insert into $TABLE_NAME values(?, ?)"

        val params = mapOf(1 to id, 2 to amount)

        executeUpdate(query, params)
        log.info("Proposal $id added to proposals table.")
    }

    /**
     * Updates the attributes of a proposal in the table of proposals.
     */
    fun updateProposal(id: String, amount: BigDecimal) {
        val query = "update $TABLE_NAME set amount = ? where id = ?"

        val params = mapOf(1 to amount, 2 to id)

        executeUpdate(query, params)
        log.info("Proposal $id updated in proposals table.")
    }

    /**
     * Retrieves the attributes of a proposal in the table of crypto values.
     */
    fun queryProposal(id: String): BigDecimal {
        val query = "select amount from $TABLE_NAME where id = ?"

        val params = mapOf(1 to id)

        val results = executeQuery(query, params) { it.getBigDecimal("amount") }

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
                amount decimal
            )"""

        executeUpdate(query, emptyMap())
        log.info("Created proposals table.")
    }
}

private const val TABLE_NAME = "proposals"