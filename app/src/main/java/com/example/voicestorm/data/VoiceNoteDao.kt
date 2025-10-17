package com.example.voicestorm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao // Anotación que lo identifica como un Data Access Object para Room
interface VoiceNoteDao {

    // Inserta una nueva nota de voz.
    @Insert
    suspend fun insert(voiceNote: VoiceNote)

    // Actualiza una nota de voz existente (por ejemplo, para añadir la transcripción).
    @Update
    suspend fun update(voiceNote: VoiceNote)

    // Obtiene todas las notas de voz, ordenadas por fecha (las más nuevas primero).
    // Usamos Flow para que la UI se actualice automáticamente cuando los datos cambien.
    @Query("SELECT * FROM voice_notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<VoiceNote>>

    // Obtiene una nota específica por su ID.
    @Query("SELECT * FROM voice_notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Long): VoiceNote?
}
