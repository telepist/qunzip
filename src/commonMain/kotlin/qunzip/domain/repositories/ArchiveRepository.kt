package qunzip.domain.repositories

import qunzip.domain.entities.*
import qunzip.domain.usecases.FileInfo
import kotlinx.coroutines.flow.Flow

interface ArchiveRepository {

    /**
     * Get basic information about an archive file
     */
    suspend fun getArchiveInfo(archivePath: String): Archive?

    /**
     * Analyze archive contents without extracting
     */
    suspend fun getArchiveContents(archivePath: String): ArchiveContents

    /**
     * Test archive integrity
     * @return true if archive is valid and can be extracted
     */
    suspend fun testArchive(archivePath: String): Boolean

    /**
     * Extract archive to specified destination with progress tracking
     * @param archivePath Path to the archive file
     * @param destinationPath Path where files should be extracted
     * @return Flow of extraction progress
     */
    suspend fun extractArchive(archivePath: String, destinationPath: String): Flow<ExtractionProgress>

    /**
     * Check if the archive format is supported by this implementation
     */
    fun isFormatSupported(format: ArchiveFormat): Boolean

    /**
     * Get list of all supported archive formats
     */
    fun getSupportedFormats(): List<ArchiveFormat>

    /**
     * Check if password is required for the archive
     */
    suspend fun isPasswordRequired(archivePath: String): Boolean

    /**
     * Extract password-protected archive (future feature)
     */
    suspend fun extractPasswordProtectedArchive(
        archivePath: String,
        destinationPath: String,
        password: String
    ): Flow<ExtractionProgress>
}