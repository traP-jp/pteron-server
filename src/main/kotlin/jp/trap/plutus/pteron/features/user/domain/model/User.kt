package jp.trap.plutus.pteron.features.user.domain.model

import jp.trap.plutus.pteron.common.domain.model.AccountId
import jp.trap.plutus.pteron.common.domain.model.UserId

@JvmInline
value class Username(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Username cannot be blank" }
    }
}

class User(
    val id: UserId,
    val name: Username,
    val accountId: AccountId,
)
