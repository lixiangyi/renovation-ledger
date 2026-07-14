package com.renovation.ledger.domain.importing

import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory hand-off from file picker / OCR into [BatchImportConfirmScreen].
 */
@Singleton
class ImportDraftStore @Inject constructor() {
    @Volatile
    var drafts: List<ImportedLineDraft> = emptyList()
        private set

    @Volatile
    var sourceLabel: String = ""
        private set

    fun set(drafts: List<ImportedLineDraft>, sourceLabel: String) {
        this.drafts = drafts
        this.sourceLabel = sourceLabel
    }

    fun clear() {
        drafts = emptyList()
        sourceLabel = ""
    }
}
