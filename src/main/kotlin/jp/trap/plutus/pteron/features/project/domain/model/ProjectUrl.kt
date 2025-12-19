package jp.trap.plutus.pteron.features.project.domain.model

import java.net.URI

@JvmInline
value class ProjectUrl(
    val value: String,
) {
    init {
        require(value.length <= 2048) { "URL must be 2048 characters or less." }
        require(isValidUrl(value)) { "URL must be a valid http or https URL." }
    }

    companion object {
        private val ALLOWED_SCHEMES = setOf("http", "https")

        private fun isValidUrl(url: String): Boolean {
            return try {
                val uri = URI(url)
                uri.scheme?.lowercase() in ALLOWED_SCHEMES &&
                    uri.host != null &&
                    uri.host.isNotBlank()
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun toString(): String = value
}
