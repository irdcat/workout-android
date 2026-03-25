package com.github.irdcat.workout.data.google

import android.content.Context

fun Context.getStringResource(id: Int): String {
    return this.resources.getString(id)
}