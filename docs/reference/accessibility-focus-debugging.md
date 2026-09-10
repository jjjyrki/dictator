# Debugging composer focus

Use a debug APK. The bubble already uses `TYPE_ACCESSIBILITY_OVERLAY`.

The app keeps a bounded, private diagnostic history for insertion problems.
It records only metadata such as failure type, target package/class, window ID,
and action outcomes—never transcript, audio, or field text. The history is
capped at 256 KiB. Use **Export diagnostics** in the Preferences section to
save it with Android's document picker, then send the exported text file.

Useful events include `target_unavailable`, `set_text_rejected`,
`set_text_unverified`, `ime_connection_unavailable`, and
`completion_discarded`. The accessibility-IME API does not return a success
value from `commitText`, so a call that returns without throwing does not prove
the editor applied the text.

Focus the ChatGPT composer, then a working field in WhatsApp. Compare event type,
package/class, source node, input focus, and accessibility focus. Logs include
editable/focused/visible/enabled/password flags, action IDs, and target eligibility.
They deliberately omit field text, hints, descriptions, and action labels.
`ACTION_SET_TEXT` is 2097152; `ACTION_SET_SELECTION` is 131072.

Check that the bubble appears, hides after leaving the field, and inserts a short
non-sensitive test phrase into the intended field in both apps. Confirm password
fields still do not show the bubble. These checks require a real device; JVM tests
only cover the capability predicate.

A focused node is eligible if it is editable or advertises either text action,
provided it is visible, enabled, and not a password. Selection-only read-only nodes
may therefore show the bubble; advertising an action does not guarantee insertion
will succeed. Existing insertion and clipboard fallbacks still apply.

Focus resolution refreshes the input-focus node, then searches at most 128 nodes
in that window for a usable node carrying actual input focus. The existing
accessibility-focus fallback remains. If no target is found, the bubble stays
attached briefly while the tree settles, then hides after one 150 ms retry with
the accessibility cache cleared.
No old target is retained. A tree larger than the search budget may still miss
its focused field. If focus resolves to the keyboard, inspect window ownership
before broadening the search to other windows.
