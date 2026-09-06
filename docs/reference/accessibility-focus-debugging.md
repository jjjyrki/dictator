# Debugging composer focus

Use a debug APK. The bubble already uses `TYPE_ACCESSIBILITY_OVERLAY`.

Enable opt-in node diagnostics:

```sh
adb shell setprop log.tag.DictatorFocus DEBUG
adb logcat -v time -s DictatorFocus:D '*:S'
```

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

Disable diagnostics when finished:

```sh
adb shell setprop log.tag.DictatorFocus INFO
```

Focus resolution refreshes the input-focus node, then searches at most 128 nodes
in that window for a usable node carrying actual input focus. The existing
accessibility-focus fallback remains. If no target is found, the bubble hides
immediately and retries once after 150 ms with the accessibility cache cleared.
No old target is retained. A tree larger than the search budget may still miss
its focused field. If focus resolves to the keyboard, inspect window ownership
before broadening the search to other windows.
