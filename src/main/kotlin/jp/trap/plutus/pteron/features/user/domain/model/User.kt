package jp.trap.plutus.pteron.features.user.domain.model

import jp.trap.plutus.pteron.common.domain.model.AccountId
import jp.trap.plutus.pteron.common.domain.model.UserId

class User(
    val id: UserId,
    val name: String,
    val accountId: AccountId,
)
