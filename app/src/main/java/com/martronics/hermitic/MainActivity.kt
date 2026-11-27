package com.martronics.hermitic

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.martronics.hermitic.ui.navigation.HermiticNavHost
import com.martronics.hermitic.ui.theme.HermiticTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HermiticTheme {
                HermiticNavHost()
            }
        }
    }
}
