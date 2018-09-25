package com.luxoft.blockchainlab.corda.hyperledger.indy.flow

import co.paralleluniverse.fibers.Suspendable
import com.luxoft.blockchainlab.corda.hyperledger.indy.contract.IndyCredentialContract
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.transactions.TransactionBuilder


/**
 * Flow to revoke previously issued claim
 */
object RevokeClaimFlow {

    /**
     * @param claimId           claim id generated by issueClaimFlow
     */
    @InitiatingFlow
    @StartableByRPC
    open class Issuer(private val claimId: String) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            try {
                // query vault for claim with id = claimid
                val claimStateIn = getIndyClaimState(claimId)
                    ?: throw RuntimeException("No such claim in vault")

                val claim = claimStateIn.state.data

                val revRegId = claim.claimInfo.claim.revRegId!!
                val credRevId = claim.claimInfo.credRevocId!!

                // revoke that claim
                indyUser().revokeClaim(revRegId, credRevId)

                val commandType = IndyCredentialContract.Command.Revoke()
                val signers = listOf(ourIdentity.owningKey)
                val command = Command(commandType, signers)

                val trxBuilder = TransactionBuilder(whoIsNotary())
                    .withItems(claimStateIn, command)

                trxBuilder.toWireTransaction(serviceHub)
                    .toLedgerTransaction(serviceHub)
                    .verify()

                val selfSignedTx = serviceHub.signInitialTransaction(trxBuilder, ourIdentity.owningKey)

                subFlow(FinalityFlow(selfSignedTx))

            } catch (ex: Exception) {
                logger.error("", ex)
                throw FlowException(ex.message)
            }
        }
    }
}