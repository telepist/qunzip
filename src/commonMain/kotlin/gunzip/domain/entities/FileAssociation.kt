package gunzip.domain.entities

data class FileAssociation(
    val extension: String,
    val applicationName: String,
    val applicationPath: String,
    val isDefault: Boolean = false
)

data class AssociationResult(
    val success: Boolean,
    val extension: String,
    val message: String? = null
)

enum class AssociationAction {
    REGISTER,
    UNREGISTER,
    CHECK_STATUS
}