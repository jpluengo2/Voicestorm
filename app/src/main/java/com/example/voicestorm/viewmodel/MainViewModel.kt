package com.example.voicestorm.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.example.voicestorm.data.AppDataBase
import com.example.voicestorm.data.VoiceNote



class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Obtenemos una referencia al DAO desde la instancia de la base de datos
    private val voiceNoteDao = AppDataBase.Companion.getDatabase(application).voiceNoteDao()

    // Usamos asLiveData() para convertir el Flow de Room en un LiveData.
    // La UI observará este LiveData para recibir actualizaciones automáticamente.
    val allNotes: LiveData<List<VoiceNote>> = voiceNoteDao.getAllNotes().asLiveData()

}
