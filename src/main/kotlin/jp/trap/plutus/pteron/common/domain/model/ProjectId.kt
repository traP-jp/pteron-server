package jp.trap.plutus.pteron.common.domain.model

import kotlin.uuid.Uuid

@JvmInline
value class ProjectId(
    val value: Uuid,
)
