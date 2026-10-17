package dev.folio.model.port

enum class DocumentMappingIssue { STRUCTURE, TOPIC_COUNT, TEXT_LIMIT, PAGE_RANGE, REGION }

/** Fixed diagnostics only: never retain the provider payload or source text as a cause. */
class DocumentMappingException(val issue: DocumentMappingIssue) : Exception(
    "Gemini topic map was rejected: " + when (issue) {
        DocumentMappingIssue.STRUCTURE -> "invalid topic structure."
        DocumentMappingIssue.TOPIC_COUNT -> "unsupported topic count."
        DocumentMappingIssue.TEXT_LIMIT -> "topic descriptions exceeded supported limits."
        DocumentMappingIssue.PAGE_RANGE -> "page references were outside the requested PDF batch."
        DocumentMappingIssue.REGION -> "topic regions were invalid."
    } + " Completed batches are saved; resume analysis to retry."
)
