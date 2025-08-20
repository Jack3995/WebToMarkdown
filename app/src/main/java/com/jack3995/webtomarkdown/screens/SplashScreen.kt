package com.jack3995.webtomarkdown.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
           /* Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(96.dp)
            )*/

            Spacer(Modifier.height(24.dp))

            Text(
                "WebToMarkdown",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )

            Spacer(Modifier.height(20.dp))

            CircularProgressIndicator(color = Color.White)
        }
    }
}
