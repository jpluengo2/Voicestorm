package com.example.voicestorm.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao // Anotación que lo identifica como un Data Access Object para Room
interface VoiceNoteDao {

    // Inserta una nueva nota de voz.
    // OnConflictStrategy.IGNORE significa que si intentas insertar una nota con un ID que ya existe, no hará nada.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(voiceNote: VoiceNote)


    // Actualiza una nota de voz existente (por ejemplo, para añadir la transcripción).
    @Update
    suspend fun update(voiceNote: VoiceNote)

    @Delete
    suspend fun delete(voiceNote: VoiceNote)

    // Obtiene todas las notas de voz, ordenadas por fecha (las más nuevas primero).
    // Usamos Flow para que la UI se actualice automáticamente cuando los datos cambien.
    @Query("SELECT * FROM voice_notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<VoiceNote>>

    // Obtiene una nota específica por su ID.
    @Query("SELECT * FROM voice_notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Int): VoiceNote?
}
