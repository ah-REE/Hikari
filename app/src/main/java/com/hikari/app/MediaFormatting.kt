package com.hikari.app

fun videoCount(count: Int) = "$count video${if (count == 1) "" else "s"}"
fun clockTime(seconds: Long): String = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
fun durationSeconds(duration: String): Long = duration.split(':').fold(0L) { total, part -> total * 60 + (part.toLongOrNull() ?: 0L) }
