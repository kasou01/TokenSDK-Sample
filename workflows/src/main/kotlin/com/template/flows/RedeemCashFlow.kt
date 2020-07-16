package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlow
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlowHandler
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.redeem.addFungibleTokensToRedeem
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import com.r3.corda.lib.tokens.workflows.utilities.ourSigningKeys
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class RedeemCashFlow(val accountName: String,
                     val currency: String,
                     val amount:Long) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val targetAccount = accountService.accountInfo(accountName)[0].state.data
        val targetAcctAnonymousParty = subFlow(RequestKeyForAccount(targetAccount))
        val notary = serviceHub.networkMapCache.notaryIdentities.single()
        var txBuilder = TransactionBuilder(notary)
        val issuerParty = serviceHub.networkMapCache.allNodes.single{ it.legalIdentities.single().name.organisation == "PartyA" }.legalIdentities.single()

        //JPY Token を取得
        val tokenType = TokenType(currency, 0)
        val amount: Amount<TokenType> = Amount(amount, tokenType)

        //実行者のVaultから必要なTokenを取得してRedeem
        val tokenCriteria = QueryCriteria.VaultQueryCriteria(externalIds = listOf(targetAccount.identifier.id))
        //issuerとholderの署名が必要
        txBuilder = addFungibleTokensToRedeem(txBuilder, serviceHub, amount, issuerParty, targetAcctAnonymousParty,tokenCriteria)
        val issuerSession = initiateFlow(issuerParty)
        subFlow(SyncKeyMappingFlow(issuerSession, txBuilder.toWireTransaction(serviceHub)))
        // Sign the transaction
        val ourSigningKeys = txBuilder.toLedgerTransaction(serviceHub).ourSigningKeys(serviceHub)
        val partialStx = serviceHub.signInitialTransaction(txBuilder, ourSigningKeys)
        val stx = subFlow(CollectSignaturesFlow(partialStx, listOf(issuerSession), ourSigningKeys))
        txBuilder.verify(serviceHub)

        return subFlow(ObserverAwareFinalityFlow(stx, listOf(issuerSession)))
    }
}
@InitiatedBy(RedeemCashFlow::class)
class RedeemCashResponder(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call(): Unit  {
        // Synchronise all confidential identities, issuer isn't involved in move transactions, so states holders may
        // not be known to this node.
        subFlow(SyncKeyMappingFlowHandler(otherPartySession))

        if (!serviceHub.myInfo.isLegalIdentity(otherPartySession.counterparty)) {
            // Perform all the checks to sign the transaction.
            subFlow(object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                }
            })
        }
        if (!serviceHub.myInfo.isLegalIdentity(otherPartySession.counterparty)) {
            // Call observer aware finality flow handler.
            subFlow(ObserverAwareFinalityFlowHandler(otherPartySession))
        }
    }
}