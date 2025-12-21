package jp.trap.plutus.pteron.config

object Environment {
    val DEBUG_MODE: Boolean by envBoolean("DEBUG_MODE", default = false)
    val PORT: Int by envInt("PORT", default = 8080)
    val HOST: String by envString("HOST", default = "0.0.0.0")

    val DATABASE_URL: String by envString("DATABASE_URL", default = "jdbc:mariadb://localhost:3306/pteron")
    val DATABASE_USER: String by envString("DATABASE_USER", default = "pteron")
    val DATABASE_PASSWORD: String by envString("DATABASE_PASSWORD", default = "pteron_password")

    val GRPC_HOST: String by envString("GRPC_HOST", default = "localhost")
    val GRPC_PORT: Int by envInt("GRPC_PORT", default = 50051)
    val GRPC_TOKEN: String by envString("GRPC_TOKEN", required = true)

    val WELCOME_BONUS_USER: Long by envLong("WELCOME_BONUS_USER", default = 1000L)
    val WELCOME_BONUS_PROJECT: Long by envLong("WELCOME_BONUS_PROJECT", default = 1000L)

    val PUBLIC_URL: String by envString("PUBLIC_URL", default = "http://localhost:8080")

    fun validate() {
        val missingVars = mutableListOf<String>()

        runCatching { DATABASE_URL }.onFailure { missingVars.add("DATABASE_URL") }
        runCatching { DATABASE_USER }.onFailure { missingVars.add("DATABASE_USER") }
        runCatching { DATABASE_PASSWORD }.onFailure { missingVars.add("DATABASE_PASSWORD") }
        runCatching { GRPC_TOKEN }.onFailure { missingVars.add("GRPC_TOKEN") }

        if (missingVars.isNotEmpty()) {
            throw IllegalStateException(
                "以下の必須環境変数が設定されていません: ${missingVars.joinToString(", ")}",
            )
        }
    }
}

private class EnvString(
    private val name: String,
    private val default: String?,
    private val required: Boolean,
) : Lazy<String> {
    private var cached: String? = null

    override val value: String
        get() {
            if (cached == null) {
                cached = System.getenv(name)
                    ?: default
                    ?: if (required) {
                        throw IllegalStateException("必須の環境変数 '$name' が設定されていません")
                    } else {
                        ""
                    }
            }
            return cached!!
        }

    override fun isInitialized(): Boolean = cached != null
}

private class EnvInt(
    private val name: String,
    private val default: Int?,
    private val required: Boolean,
) : Lazy<Int> {
    private var cached: Int? = null
    private var initialized = false

    override val value: Int
        get() {
            if (!initialized) {
                val strValue = System.getenv(name)
                cached = strValue?.toIntOrNull()
                    ?: default
                    ?: if (required) {
                        throw IllegalStateException("必須の環境変数 '$name' が設定されていません")
                    } else if (strValue != null) {
                        throw IllegalStateException("環境変数 '$name' の値 '$strValue' は整数ではありません")
                    } else {
                        0
                    }
                initialized = true
            }
            return cached!!
        }

    override fun isInitialized(): Boolean = initialized
}

private class EnvBoolean(
    private val name: String,
    private val default: Boolean,
) : Lazy<Boolean> {
    private var cached: Boolean? = null

    override val value: Boolean
        get() {
            if (cached == null) {
                cached = System.getenv(name)?.toBooleanStrictOrNull() ?: default
            }
            return cached!!
        }

    override fun isInitialized(): Boolean = cached != null
}

private class EnvLong(
    private val name: String,
    private val default: Long?,
    private val required: Boolean,
) : Lazy<Long> {
    private var cached: Long? = null
    private var initialized = false

    override val value: Long
        get() {
            if (!initialized) {
                val strValue = System.getenv(name)
                cached = strValue?.toLongOrNull()
                    ?: default
                    ?: if (required) {
                        throw IllegalStateException("必須の環境変数 '$name' が設定されていません")
                    } else if (strValue != null) {
                        throw IllegalStateException("環境変数 '$name' の値 '$strValue' は整数ではありません")
                    } else {
                        0L
                    }
                initialized = true
            }
            return cached!!
        }

    override fun isInitialized(): Boolean = initialized
}

private fun envString(
    name: String,
    default: String? = null,
    required: Boolean = false,
): Lazy<String> = EnvString(name, default, required)

private fun envInt(
    name: String,
    default: Int? = null,
    required: Boolean = false,
): Lazy<Int> = EnvInt(name, default, required)

private fun envBoolean(
    name: String,
    default: Boolean = false,
): Lazy<Boolean> = EnvBoolean(name, default)

private fun envLong(
    name: String,
    default: Long? = null,
    required: Boolean = false,
): Lazy<Long> = EnvLong(name, default, required)
