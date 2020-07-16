package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestAccountInfoFlow
import com.r3.corda.lib.accounts.workflows.flows.RequestAccountInfoHandlerFlow
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccountFlow
import com.r3.corda.lib.accounts.workflows.flows.SendKeyForAccountFlow
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.issue.addIssueTokens
import com.r3.corda.lib.tokens.workflows.flows.redeem.addFungibleTokensToRedeem
import com.r3.corda.lib.tokens.workflows.utilities.tokenBalance
import net.corda.core.contracts.Amount
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import com.r3.corda.lib.tokens.money.FiatCurrency.Companion.getInstance
import net.corda.core.identity.AnonymousParty
import java.util.*

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class Initiator : FlowLogic<Unit>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call() {
        // Initiator flow logic goes here.
    }
}

@InitiatedBy(Initiator::class)
class Responder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Responder flow logic goes here.
    }
}

@InitiatingFlow
@StartableByRPC
class Issue(val accountUUID: String,val accountHostParty : Party) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()
    @Suspendable
    override fun call() : SignedTransaction{
        val accountHostSession = initiateFlow(accountHostParty)
        val acctInfo : AccountInfo? = subFlow(RequestAccountInfoFlow(UUID.fromString(accountUUID), accountHostSession))
        if(acctInfo == null)
            throw FlowException("not found account : ${accountUUID} in ${accountHostParty.name.toString()}")
        val accountParty = subFlow(RequestKeyForAccountFlow(
                accountInfo = acctInfo,
                hostSession = accountHostSession
        ))
        val jpyToken = createFungibleToken("JPY", 1000, accountParty)
        val gbToken = createFungibleToken("GB", 1000, accountParty)
        val notary = serviceHub.networkMapCache.notaryIdentities.single()
        var txBuilder = TransactionBuilder(notary)
        //may be add other output input
        //....

        //add token to txBuilder
        txBuilder = addIssueTokens(txBuilder, listOf(jpyToken, gbToken))
        txBuilder.verify(serviceHub)
        // Sign the transaction
        val ptx = serviceHub.signInitialTransaction(txBuilder, ourIdentity.owningKey)
        val listOfSession: List<FlowSession>
        if(accountHostParty == ourIdentity)
            listOfSession = listOf()
        else{
            listOfSession = listOf(accountHostSession)
        }
        return subFlow<SignedTransaction>(FinalityFlow(ptx, listOfSession))
    }
    fun createFungibleToken(symbol:String,amout:Long,target : AbstractParty) : FungibleToken{
        val tokenType = TokenType(symbol, 0)
        val issuedTokenType = IssuedTokenType(ourIdentity, tokenType)
        val amount = Amount<IssuedTokenType>(amout, issuedTokenType)
        return FungibleToken(amount, target)
    }
}


@InitiatedBy(Issue::class)
class IssueResponder(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction  {
        subFlow(RequestAccountInfoHandlerFlow(otherPartySession))
        subFlow(SendKeyForAccountFlow(otherPartySession))
        return subFlow(ReceiveFinalityFlow(otherPartySession))
    }
}

@InitiatingFlow
@StartableByRPC
class Change(val otherParty: Party) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():SignedTransaction{

        val notary = serviceHub.networkMapCache.notaryIdentities.single()
        val txBuilder = TransactionBuilder(notary)

        val gbToken = createFungibleToken("GB", 32,otherParty)
        //32 JPY Token を取得
        val tokenType = TokenType("JPY", 0)
        val amount: Amount<TokenType> = Amount(32, tokenType)

        //実行者のVaultから必要なTokenを取得してRedeem
        addFungibleTokensToRedeem(txBuilder, serviceHub, amount, otherParty, ourIdentity)
        addIssueTokens(txBuilder,gbToken)
        txBuilder.verify(serviceHub)
        // Sign the transaction
        val ptx = serviceHub.signInitialTransaction(txBuilder, ourIdentity.owningKey)
        // Instantiate a network session with the shareholder
        val holderSession = initiateFlow(otherParty)
        val sessions = listOf(holderSession)

        // Ask the shareholder to sign the transaction
        val stx = subFlow(CollectSignaturesFlow(ptx, listOf(holderSession)))
        return subFlow<SignedTransaction>(FinalityFlow(stx, sessions))
    }
    fun createFungibleToken(symbol:String,amout:Long,target : AbstractParty) : FungibleToken{

        val tokenType = TokenType(symbol, 0)
        val issuedTokenType = IssuedTokenType(ourIdentity, tokenType)
        val amount = Amount<IssuedTokenType>(amout, issuedTokenType)
        return FungibleToken(amount, target)
    }
}

@InitiatedBy(Change::class)
class ChangeResponder(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction  {
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {

            }
        }
        val txId = subFlow(signTransactionFlow).id
        return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
    }
}

@InitiatingFlow
@StartableByRPC
class GetBalance(private val currencyCode: String) : FlowLogic<Amount<TokenType>>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    @Throws(FlowException::class)
    override fun call(): Amount<TokenType> {
        val tokenType = TokenType(currencyCode, 0)
        return serviceHub.vaultService.tokenBalance(tokenType)
    }
}
