package jp.trap.plutus.pteron.features.transaction.domain.model

import kotlin.uuid.Uuid

@JvmInline
value class BillId(
    val value: Uuid,
)
