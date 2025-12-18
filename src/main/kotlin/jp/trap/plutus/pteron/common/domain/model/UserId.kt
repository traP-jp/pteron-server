package jp.trap.plutus.pteron.common.domain.model

import kotlin.uuid.Uuid

@JvmInline
value class UserId(
    val value: Uuid,
)
