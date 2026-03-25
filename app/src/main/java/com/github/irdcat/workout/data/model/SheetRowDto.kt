package com.github.irdcat.workout.data.model

data class SheetRowDto(
    val rowIndex: Int,
    val sheetName: String,
    val values: List<String>,
)
