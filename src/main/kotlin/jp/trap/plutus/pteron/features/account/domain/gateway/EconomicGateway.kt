package jp.trap.plutus.pteron.features.account.domain.gateway

import jp.trap.plutus.pteron.common.domain.model.AccountId
import jp.trap.plutus.pteron.features.account.domain.model.Account

interface EconomicGateway {
    suspend fun findAccountById(accountId: AccountId): Account?

    suspend fun findAccountsByIds(accountIds: List<AccountId>): List<Account>

    suspend fun transfer(
        from: AccountId,
        to: AccountId,
        amount: Long,
    )

    suspend fun createAccount(canOverdraft: Boolean): Account
}
