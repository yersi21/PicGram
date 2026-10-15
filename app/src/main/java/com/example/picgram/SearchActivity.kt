package com.example.picgram

import com.example.picgram.ui.screens.*
import com.example.picgram.ui.*

import com.example.picgram.presentation.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.picgram.ui.theme.*

/**
 * Pantalla de busqueda de usuarios (RF-06).
 * Busca en tiempo real usando debounce de 400ms para no sobrecargar la API.
 * Al tocar un resultado abre UserProfileActivity con el perfil del usuario.
 */
class SearchActivity : ComponentActivity() {
    private val model by lazy { screenViewModel { SearchViewModel(it) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ObserveNotices(model)
            val state by model.uiState.collectAsStateWithLifecycle()
            PicGramTheme {
                SearchScreen(state, onQueryChange = model::search, onBackClicked = { finish() },
                    onUserClicked = { openUserProfile(it.id, it.username) })
            }
        }
    }
    override fun onResume() { super.onResume(); model.search() }
}
