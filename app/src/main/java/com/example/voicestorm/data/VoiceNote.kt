package com.example.voicestorm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "voice_notes") // Define el nombre de la tabla
data class VoiceNote(
    @PrimaryKey(autoGenerate = true) // El ID será la clave primaria y se autogenerará
    val id: Long = 0,

    // 1. Campo para la fecha. Room sabe cómo guardar tipos primitivos y comunes como Date.
    val createdAt: Date,

    // 2. Título de la nota
    val title: String,

    // 3. Nombre del archivo de audio. Lo guardamos como String.
    val audioFileName: String,

    // 4. Ruta al archivo de texto con la transcripción. Puede ser nulo hasta que se genere.
    val transcriptFilePath: String?
)
