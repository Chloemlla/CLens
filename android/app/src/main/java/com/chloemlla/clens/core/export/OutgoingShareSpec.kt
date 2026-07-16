package com.chloemlla.clens.core.export

/**
 * Pure share-intent policy for Android 14+ implicit-intent / URI-grant rules.
 * Call sites still use Android Intent APIs; this encodes the required shape for tests.
 */
data class OutgoingShareSpec(
    val action: String = ACTION_SEND,
    val mimeType: String,
    val subject: String,
    val text: String? = null,
    val hasStream: Boolean = false,
) {
    val requiresReadUriGrant: Boolean get() = hasStream
    val requiresClipData: Boolean get() = hasStream
    val isChooserFriendlyExternalShare: Boolean get() = action == ACTION_SEND

    init {
        require(mimeType.isNotBlank()) { "mimeType required" }
        require(subject.isNotBlank()) { "subject required" }
        if (hasStream) {
            require(text == null) { "stream share should not also set text body in this helper" }
        } else {
            require(!text.isNullOrEmpty()) { "text share requires body" }
        }
    }

    companion object {
        const val ACTION_SEND: String = "android.intent.action.SEND"

        fun text(subject: String, body: String): OutgoingShareSpec =
            OutgoingShareSpec(
                mimeType = "text/plain",
                subject = subject,
                text = body,
                hasStream = false,
            )

        fun file(subject: String, mimeType: String): OutgoingShareSpec =
            OutgoingShareSpec(
                mimeType = mimeType,
                subject = subject,
                text = null,
                hasStream = true,
            )
    }
}
