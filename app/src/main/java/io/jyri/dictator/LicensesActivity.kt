package io.jyri.dictator

import android.app.Activity
import android.os.Bundle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textview.MaterialTextView

class LicensesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_licenses)

        findViewById<MaterialToolbar>(R.id.licensesToolbar).setNavigationOnClickListener { finish() }
        findViewById<MaterialTextView>(R.id.licensesBody).text =
            resources.openRawResource(R.raw.third_party_licenses)
                .bufferedReader()
                .use { it.readText() }
    }
}
