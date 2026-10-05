package com.example.gather

data class Stop(
    val id: Long,
    val hangoutId: Long,
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val position: Int,
    val plannedTime: String
)