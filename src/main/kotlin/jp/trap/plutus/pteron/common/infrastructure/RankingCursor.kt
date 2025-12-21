package jp.trap.plutus.pteron.common.infrastructure

import java.nio.ByteBuffer
import java.util.*
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * ランキング用カーソルユーティリティ
 *
 * カーソルはopaque tokenとして実装され、バイナリ形式でエンコードされます:
 * - 8 bytes: rank (Long, BigEndian)
 * - 16 bytes: UUID (128bit, BigEndian)
 *
 * 最終的にBase64 URL-safeでエンコードされます。
 */
object RankingCursor {
    private const val CURSOR_SIZE = 24 // 8 (Long) + 16 (UUID)
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    /**
     * rank と id からカーソルトークンを生成
     */
    fun encode(
        rank: Long,
        id: Uuid,
    ): String {
        val buffer = ByteBuffer.allocate(CURSOR_SIZE)
        buffer.putLong(rank)
        val javaUuid = id.toJavaUuid()
        buffer.putLong(javaUuid.mostSignificantBits)
        buffer.putLong(javaUuid.leastSignificantBits)
        return encoder.encodeToString(buffer.array())
    }

    /**
     * カーソルトークンをデコード
     * @return Pair<Long, Uuid>? (無効なカーソルの場合はnull)
     */
    fun decode(cursor: String): Pair<Long, Uuid>? {
        return try {
            val bytes = decoder.decode(cursor)
            if (bytes.size != CURSOR_SIZE) return null

            val buffer = ByteBuffer.wrap(bytes)
            val rank = buffer.getLong()
            val mostSigBits = buffer.getLong()
            val leastSigBits = buffer.getLong()

            val uuid = UUID(mostSigBits, leastSigBits).toKotlinUuid()

            Pair(rank, uuid)
        } catch (_: Exception) {
            null
        }
    }
}
