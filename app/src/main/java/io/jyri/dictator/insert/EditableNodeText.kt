package io.jyri.dictator.insert

import android.view.accessibility.AccessibilityNodeInfo

/** Returns user-entered text, excluding an accessibility-exposed placeholder. */
fun AccessibilityNodeInfo.actualText(): String = actualText(
    fieldText = text?.toString().orEmpty(),
    hint = hintText?.toString(),
    editable = isEditable,
    showingHintText = isShowingHintText,
    textIsPlaceholder = isUnselectedWhatsAppComposer(),
)

/**
 * WhatsApp exposes its empty composer label as text, without a hint flag or
 * hint text. In the focused empty composer it also exposes no selection;
 * actual focused drafts expose a cursor or selection.
 */
private fun AccessibilityNodeInfo.isUnselectedWhatsAppComposer(): Boolean =
    packageName?.toString() == WHATSAPP_PACKAGE &&
        viewIdResourceName == WHATSAPP_ENTRY_ID &&
        textSelectionStart < 0 &&
        textSelectionEnd < 0

internal fun actualText(
    fieldText: String,
    hint: String?,
    editable: Boolean,
    showingHintText: Boolean,
    textIsPlaceholder: Boolean = false,
): String {
    if (showingHintText || textIsPlaceholder) return ""
    if (editable && !hint.isNullOrEmpty() && fieldText == hint) return ""
    return fieldText
}

private const val WHATSAPP_PACKAGE = "com.whatsapp"
private const val WHATSAPP_ENTRY_ID = "com.whatsapp:id/entry"
