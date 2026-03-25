package com.github.irdcat.workout.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun List(modifier: Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(modifier) {
        content()
    }
}

@Composable
fun ListItem(onClick: () -> Unit = {}, content: @Composable () -> Unit) {
    Row(modifier = Modifier
        .clickable { onClick.invoke() }
        .fillMaxWidth()
        .padding(16.dp)) {
        content.invoke()
    }
    Separator(1.dp)
}