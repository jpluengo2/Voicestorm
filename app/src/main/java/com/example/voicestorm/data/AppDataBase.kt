package com.example.voicestorm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import java.util.Date

// Es importante añadir los TypeConverters si Room no sabe cómo manejar un tipo de dato.
// En este caso, para el tipo 'Date'.
@Database(entities = [VoiceNote::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun voiceNoteDao(): VoiceNoteDao

    companion object {
        // La anotación @Volatile asegura que el valor de INSTANCE sea siempre el más actualizado
        // y visible para todos los hilos de ejecución.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            // synchronized evita que múltiples hilos creen instancias de la BD al mismo tiempo.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "voicestorm_database" // Nombre del archivo de la base de datos
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Room no sabe cómo guardar un objeto 'Date'. Necesitamos un conversor.
class Converters {
    @androidx.room.TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @androidx.room.TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
