package io.jyri.dictator

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class LicensesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_licenses)

        findViewById<Button>(R.id.licensesBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.licensesBody).text =
            resources.openRawResource(R.raw.third_party_licenses)
                .bufferedReader()
                .use { it.readText() }
    }
}
