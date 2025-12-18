package jp.trap.plutus.pteron.features.account.domain.model

import jp.trap.plutus.pteron.common.domain.model.AccountId

class Account(
    val accountId: AccountId,
    val balance: Long,
    val canOverdraft: Boolean,
)
