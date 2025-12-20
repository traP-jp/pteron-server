package jp.trap.plutus.pteron.common.exception

/**
 * HTTPステータスコード400: Bad Request
 * バリデーションエラー、不正なリクエストパラメータなど
 */
class BadRequestException(
    message: String,
) : Exception(message)

/**
 * HTTPステータスコード401: Unauthorized
 * 認証エラー
 */
class UnauthorizedException(
    message: String = "Unauthorized",
) : Exception(message)

/**
 * HTTPステータスコード403: Forbidden
 * 権限不足
 */
class ForbiddenException(
    message: String,
) : Exception(message)

/**
 * HTTPステータスコード404: Not Found
 * リソースが見つからない
 */
class NotFoundException(
    message: String,
) : Exception(message)

/**
 * HTTPステータスコード409: Conflict
 * 競合エラー（既に処理済み、重複リクエストなど）
 */
class ConflictException(
    message: String,
) : Exception(message)
