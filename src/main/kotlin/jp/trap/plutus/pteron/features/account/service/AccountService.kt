package jp.trap.plutus.pteron.features.account.service

import jp.trap.plutus.pteron.common.domain.model.AccountId
import jp.trap.plutus.pteron.features.account.domain.gateway.EconomicGateway
import jp.trap.plutus.pteron.features.account.domain.model.Account
import org.koin.core.annotation.Single

@Single
class AccountService(
    private val economicGateway: EconomicGateway,
) {
    suspend fun getAccountById(id: AccountId): Account =
        economicGateway.findAccountById(id)
            ?: throw IllegalStateException("Account not found: $id")
}
