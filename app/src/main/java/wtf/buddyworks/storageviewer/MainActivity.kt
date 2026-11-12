package wtf.buddyworks.storageviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import wtf.buddyworks.storageviewer.ui.AppRoot
import wtf.buddyworks.storageviewer.ui.theme.StorageViewerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            StorageViewerTheme {
                AppRoot()
            }
        }
    }
}
