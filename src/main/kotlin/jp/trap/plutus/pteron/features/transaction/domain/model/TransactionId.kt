package jp.trap.plutus.pteron.features.transaction.domain.model

import kotlin.uuid.Uuid

@JvmInline
value class TransactionId(
    val value: Uuid,
)
