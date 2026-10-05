package com.example.gather

data class Hangout(
    val id: Long = 0,
    val title: String,
    val city: String,
    val whenText: String,
    val notes: String
)