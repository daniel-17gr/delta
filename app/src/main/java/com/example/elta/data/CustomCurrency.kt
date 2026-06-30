package com.example.elta.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_currencies")
data class CustomCurrency(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,      // 3 letters (case-insensitive, uppercased on save)
    val symbol: String     // length < 3
)
