package jp.trap.plutus.pteron.common.infrastructure

import java.nio.ByteBuffer
import java.util.*
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * カーソルベースのページネーション用ユーティリティ
 *
 * カーソルはopaque token として実装され、バイナリ形式でエンコードされます:
 * - 8 bytes: epoch milliseconds (Long, BigEndian)
 * - 16 bytes: UUID (128bit, BigEndian)
 *
 * 最終的にBase64 URL-safeでエンコードされます。
 */
object PaginationCursor {
    private const val CURSOR_SIZE = 24 // 8 (Long) + 16 (UUID)
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    /**
     * createdAt と id からカーソルトークンを生成
     */
    fun encode(createdAt: Instant, id: Uuid): String {
        val buffer = ByteBuffer.allocate(CURSOR_SIZE)
        buffer.putLong(createdAt.toEpochMilliseconds())
        val javaUuid = id.toJavaUuid()
        buffer.putLong(javaUuid.mostSignificantBits)
        buffer.putLong(javaUuid.leastSignificantBits)
        return encoder.encodeToString(buffer.array())
    }

    /**
     * カーソルトークンをデコード
     * @return Pair<Instant, Uuid>? (無効なカーソルの場合はnull)
     */
    fun decode(cursor: String): Pair<Instant, Uuid>? {
        return try {
            val bytes = decoder.decode(cursor)
            if (bytes.size != CURSOR_SIZE) return null

            val buffer = ByteBuffer.wrap(bytes)
            val epochMillis = buffer.getLong()
            val mostSigBits = buffer.getLong()
            val leastSigBits = buffer.getLong()

            val instant = Instant.fromEpochMilliseconds(epochMillis)
            val uuid = UUID(mostSigBits, leastSigBits).toKotlinUuid()

            Pair(instant, uuid)
        } catch (_: Exception) {
            null
        }
    }
}
