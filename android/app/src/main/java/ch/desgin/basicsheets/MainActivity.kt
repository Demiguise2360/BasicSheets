package ch.desgin.basicsheets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ch.desgin.basicsheets.ui.AppRoot
import ch.desgin.basicsheets.ui.theme.BasicSheetsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BasicSheetsTheme {
                AppRoot()
            }
        }
    }
}
