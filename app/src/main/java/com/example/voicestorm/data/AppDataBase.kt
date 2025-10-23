package com.example.voicestorm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import java.util.Date

// Your entity and converter classes
import com.example.voicestorm.data.VoiceNote
import com.example.voicestorm.data.VoiceNoteDao

// Es importante añadir los TypeConverters si Room no sabe cómo manejar un tipo de dato.
// En este caso, para el tipo 'Date'.
// Annotate the class to be a Room database, list the entities, and set the version.
@Database(entities = [VoiceNote::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDataBase : RoomDatabase() {

    // 2. Define an abstract method to get the DAO for the VoiceNote entity.
    abstract fun voiceNoteDao(): VoiceNoteDao

    // 3. Create a companion object to provide a singleton instance of the database.
    companion object {
        // The '@Volatile' annotation ensures that the INSTANCE variable is always up-to-date
        // and the same for all execution threads.
        @Volatile
        private var INSTANCE: AppDataBase? = null

        fun getDatabase(context: Context): AppDataBase {
            // Return the existing instance if it's not null.
            // If it is null, create the database in a synchronized block to ensure thread safety.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDataBase::class.java,
                    "voicestorm_database" // This will be the name of your database file.
                ).build()
                INSTANCE = instance
                // Return the newly created instance.
                instance
            }
        }
    }
}

