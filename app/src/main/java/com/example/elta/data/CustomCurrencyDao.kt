package com.example.elta.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCurrencyDao {
    @Query("SELECT * FROM custom_currencies ORDER BY code ASC")
    fun getAllCustomCurrencies(): Flow<List<CustomCurrency>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomCurrency(customCurrency: CustomCurrency)

    @Delete
    suspend fun deleteCustomCurrency(customCurrency: CustomCurrency)

    @Query("SELECT COUNT(*) FROM custom_currencies")
    suspend fun getCustomCurrencyCount(): Int
}
