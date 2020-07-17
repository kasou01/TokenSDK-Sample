package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.*
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlow
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlowHandler
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.of
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
import com.r3.corda.lib.tokens.selection.TokenQueryBy
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.flows.redeem.addTokensToRedeem
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import com.r3.corda.lib.tokens.workflows.utilities.heldTokensByToken
import com.r3.corda.lib.tokens.workflows.utilities.ourSigningKeys
import com.template.contracts.TemplateContract
import com.template.states.TemplateState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.ColumnPredicate
import net.corda.core.node.services.vault.QueryCriteria
import java.math.RoundingMode
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
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
class ChangeOnSameNode(val fromUUID:UUID ,val toUUID : UUID, val amout : Long) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():SignedTransaction{
        val fromAccountInfo = accountService.accountInfo(fromUUID)?.state?.data?:throw FlowException("")
        val fromParty = subFlow(RequestKeyForAccount(fromAccountInfo))

        val toAccountInfo = accountService.accountInfo(toUUID)?.state?.data?:throw FlowException("")
        val toParty = subFlow(RequestKeyForAccount(toAccountInfo))

        val notary = serviceHub.networkMapCache.notaryIdentities.single()
        var txBuilder = TransactionBuilder(notary)
        val issuerParty = serviceHub.networkMapCache.allNodes.single{ it.legalIdentities.single().name.organisation == "PartyA" }.legalIdentities.single()
        val issuerSession = initiateFlow(issuerParty)
        val toPartySession = initiateFlow(toAccountInfo.host)
        //例JPY⇒GBに変更
        //JPY Token を取得 実行者のVaultから必要なTokenを取得してRedeem
        val tokenType = TokenType("JPY", 0)
        val amount: Amount<TokenType> = Amount(amout, tokenType)
        //当日分のToken
        val (start,end) = createTimeWindow()
        val recordedBetweenExpression = QueryCriteria.TimeCondition(
                QueryCriteria.TimeInstantType.RECORDED,
                ColumnPredicate.Between(start, end))
        val tokenCriteria = QueryCriteria.VaultQueryCriteria(externalIds = listOf(fromAccountInfo.identifier.id)).withTimeCondition(recordedBetweenExpression)
        //消費すべきTokenをInputに追加、残高TokenをOutPutに追加
        txBuilder = addFungibleTokensToRedeem(txBuilder, serviceHub, amount, issuerParty, fromParty,tokenCriteria)
        //Receiver貰うべきToken
        val gbTokenForReceiver = createFungibleToken("GB", amout,toParty,issuerParty)
        //キャッシュバック for sender
        val gbTokenForSender = createFungibleToken("GB", amout.toBigDecimal().multiply(0.5.toBigDecimal()).setScale(-1,RoundingMode.DOWN).toLong(),fromParty,issuerParty)
        //txBuilderにToken追加
        txBuilder = addIssueTokens(txBuilder, listOf(gbTokenForSender,gbTokenForReceiver))
        //txBuilderに取引情報追加
        val tradeState = TemplateState("${fromAccountInfo.name} send to ${toAccountInfo.name}. amout: ${amout}", listOf(fromParty,toParty))
        val tradeCommand = Command(TemplateContract.Commands.Action(), tradeState.participants.map { it.owningKey })
        txBuilder.addOutputState(tradeState,TemplateContract.ID).addCommand(tradeCommand)
        txBuilder.verify(serviceHub)
        //publicKey同期
        //必要なSinger：issuer,from,to
        val sessionList = mutableListOf<FlowSession>()
        if(toPartySession.counterparty != ourIdentity){
            subFlow(SyncKeyMappingFlow(toPartySession, txBuilder.toWireTransaction(serviceHub)))
            sessionList.add(toPartySession)
        }
        if(issuerSession.counterparty != ourIdentity){
            subFlow(SyncKeyMappingFlow(issuerSession, txBuilder.toWireTransaction(serviceHub)))
            sessionList.add(issuerSession)
        }
        // Sign the transaction　　　
        val ourSigningKeys = txBuilder.toLedgerTransaction(serviceHub).ourSigningKeys(serviceHub)
        val partialStx = serviceHub.signInitialTransaction(txBuilder, ourSigningKeys)

        val stx = subFlow(CollectSignaturesFlow(partialStx, sessionList, ourSigningKeys))

        return subFlow(ObserverAwareFinalityFlow(stx, sessionList))
    }
    fun createFungibleToken(symbol:String,amout:Long,holder : AbstractParty,issuer : Party) : FungibleToken{

        val tokenType = TokenType(symbol, 0)
        val issuedTokenType = IssuedTokenType(issuer, tokenType)
        val amount = Amount<IssuedTokenType>(amout, issuedTokenType)
        return FungibleToken(amount, holder)
    }
    fun createTimeWindow():Pair<Instant,Instant>{
        val currentDateTime = LocalDateTime.now().atZone(ZoneId.of("Asia/Tokyo"))
        val start = currentDateTime.with(LocalTime.of(0, 0,0)).toInstant()
        val end = currentDateTime.plusDays(1).with(LocalTime.of(0, 0,0)).toInstant()
        return Pair(start,end)
    }
}

@InitiatedBy(ChangeOnSameNode::class)
class ChangeResponder(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
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

@InitiatingFlow
@StartableByRPC
class GetBalance(val currencyCode: String,val accountName : String,val hostParty: Party) : FlowLogic<Amount<IssuedTokenType>>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    @Throws(FlowException::class)
    override fun call(): Amount<IssuedTokenType> {
        val myAccount = accountService.accountInfo(accountName).single { it.state.data.host == ourIdentity }.state.data
        val criteria = QueryCriteria.VaultQueryCriteria(externalIds = listOf(myAccount.identifier.id))
        val asset = serviceHub.vaultService.queryBy(FungibleToken::class.java, criteria).states
        return asset.single { it.state.data.tokenType.tokenIdentifier.equals(currencyCode) }.state.data.amount
    }


}
