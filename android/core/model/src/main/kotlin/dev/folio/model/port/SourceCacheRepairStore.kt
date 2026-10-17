package dev.folio.model.port

/** Metadata only: source payloads never enter UI or provider repair context. */
data class SourceCacheRepairPreview(val fingerprint: String, val totalRows: Int,
    val damagedPages: Int, val damagedReports: Int) {
    val needsRepair get() = damagedPages > 0 || damagedReports > 0
}
data class SourceCacheRepairReceipt(val backupId: String, val removedPages: Int,
    val clearedReports: Int, val retainedPages: Int)
interface SourceCacheRepairStore {
    suspend fun previewRepair(): SourceCacheRepairPreview
    suspend fun repair(expectedFingerprint: String): SourceCacheRepairReceipt
}
interface SourceCacheRepairApproval {
    fun canConfirm(token: String): Boolean
}
