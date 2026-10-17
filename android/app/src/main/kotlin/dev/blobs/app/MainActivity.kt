package dev.blobs.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.blobs.app.ui.BlobsApp

class MainActivity : ComponentActivity() {
    override fun onStop() {
        if (!isChangingConfigurations) {
            (application as BlobsApplication).container.work.stop(
                background = true
            )
        }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BlobsApplication).container
        setContent { BlobsApp(container) }
    }
}
