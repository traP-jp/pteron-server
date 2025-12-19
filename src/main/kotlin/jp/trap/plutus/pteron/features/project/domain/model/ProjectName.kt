package jp.trap.plutus.pteron.features.project.domain.model

@JvmInline
value class ProjectName(
    val value: String,
) {
    init {
        require(value.length <= 32) { "Project name must be 32 characters or less." }
        require(value.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            "Project name must contain only alphanumeric characters and underscores."
        }
    }

    val normalized: String
        get() = value.lowercase()

    fun equalsIgnoreCase(other: ProjectName): Boolean = this.normalized == other.normalized

    fun equalsIgnoreCase(other: String): Boolean = this.normalized == other.lowercase()

    override fun toString(): String = value
}
