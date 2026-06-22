package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import com.example.data.api.RetrofitClient
import com.example.ui.GH4AAppContent
import com.example.ui.GitHubViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize network caching limits
        RetrofitClient.initialize(applicationContext)
        
        // Initialize image loading disk and memory cache limits for lowest possible RAM usage
        val imageLoader = ImageLoader.Builder(applicationContext)
            .memoryCache {
                coil.memory.MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.10) // low memory footprint, only 10% maximum RAM allocation
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(10 * 1024 * 1024) // Exactly 10 MB maximum disk footprint
                    .build()
            }
            .allowHardware(true)
            .crossfade(false) // Zero animations
            .build()
        Coil.setImageLoader(imageLoader)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Initialize the central View Model
                val viewModel: GitHubViewModel = viewModel()
                GH4AAppContent(viewModel = viewModel)
            }
        }
    }
}
