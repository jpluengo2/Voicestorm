package com.example.voicestorm.viewmodel

import android.app.Application
import androidx.activity.result.launch
//import kotlinx.coroutines.launch
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.voicestorm.data.AppDataBase
import com.example.voicestorm.data.VoiceNote
import com.example.voicestorm.data.VoiceNoteDao
import kotlinx.coroutines.Dispatchers


// El ViewModel necesita el contexto de la aplicación para obtener la instancia de la base de datos.
// Por eso hereda de AndroidViewModel en lugar de ViewModel.
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // 1. Declara el DAO y el LiveData, pero no los inicialices aquí.
    private val voiceNoteDao: VoiceNoteDao
    val allNotes: LiveData<List<VoiceNote>>

    // 2. Usa un bloque 'init' para la inicialización.
    // Este bloque se ejecuta cuando se crea una instancia del ViewModel.
    init {
        // Obtenemos la instancia del DAO de forma segura.
        voiceNoteDao = AppDataBase.getDatabase(application).voiceNoteDao()

        // Inicializamos el LiveData a partir del Flow.
        allNotes = voiceNoteDao.getAllNotes().asLiveData()

        // --- !! ESTA ES LA LÍNEA CLAVE DE LA SOLUCIÓN !! ---
        // Forzamos una operación de lectura para asegurar que la BD se cree.
        // Lo hacemos en una corrutina para no bloquear el hilo principal.
        //viewModelScope.launch(Dispatchers.IO) {
        //    voiceNoteDao.getAllNotes().collect {} // Esta es la "llamada de despertador".
        //}
    }
}
