package com.example.picgram

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Flat pure-black lettering on transparency, without reflections or a backing panel. */
@Composable
fun PicGramWordmark(modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(
        modifier = modifier.padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(R.drawable.picgram_wordmark_flat_black),
            contentDescription = "PicGram", modifier = Modifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(Color.Black), contentScale = ContentScale.Fit)
    }
}
