package com.example.voicestorm.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.voicestorm.adapters.VoiceNoteAdapter
import com.example.voicestorm.activities.ViewNoteActivity
import com.example.voicestorm.databinding.ActivityMainBinding
import com.example.voicestorm.viewmodel.MainViewModel
import android.media.MediaPlayer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.voicestorm.data.AppDataBase
import com.example.voicestorm.data.VoiceNote
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.IOException
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 1. Inicializamos el ViewModel usando 'by viewModels()'
    // Esto lo crea y lo asocia automáticamente al ciclo de vida de esta Activity.
    private val voiceNoteViewModel: MainViewModel by viewModels()

    // 2. Declaramos nuestro adaptador
    private lateinit var adapter: VoiceNoteAdapter

    // --- Lógica de Reproducción ---
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflamos el binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configuramos la barra de herramientas (Toolbar)
        setSupportActionBar(binding.toolbarMain)

        // Configuramos el RecyclerView y el Adaptador
        setupRecyclerView()

        // Observamos la "tubería" (el LiveData)
        // Este bloque se ejecutará cada vez que los datos en la BBDD cambien.
        voiceNoteViewModel.allNotes.observe(this) { notes ->
            // Cada vez que los datos cambien en la DB, este bloque se ejecutará.
            // Actualizamos el adapter con la nueva lista de notas.
            adapter.updateNotes(notes)
        }

        // 7. Configuramos el botón flotante (FAB)
        binding.fabAddNote.setOnClickListener {
            val intent = Intent(this, AddNoteActivity::class.java)
            startActivity(intent)
        }
    }

    //Función helper para configurar el RecyclerView y su adaptador.
    private fun setupRecyclerView() {
        // Define the action for playing a note
        val onPlayClick: (VoiceNote) -> Unit = { note ->
            onNotePlay(note)
        }

        // Definimos la acción de clic de forma clara
        val onNoteClick: (VoiceNote) -> Unit = { note ->
            // Detenemos la reproducción si algo estaba sonando en la lista
            stopPlayback()
            adapter.currentlyPlayingId = null

            // Creamos el Intent para abrir la actividad de detalle
            val intent = Intent(this, ViewNoteActivity::class.java)
            // Añadimos el ID de la nota. 'note.id' aquí es la clave.
            // Este 'id' viene de la base de datos y es el correcto.
            intent.putExtra(ViewNoteActivity.NOTE_ID_KEY, note.id)
            // Iniciamos la actividad
            startActivity(intent)
        }

        // 1. Create the adapter instance, passing BOTH click handlers
        adapter = VoiceNoteAdapter(
            notes = emptyList(),
            onNoteClicked = onNoteClick,
            onPlayClicked = onPlayClick
        )

        // 2. Asignamos el adaptador al RecyclerView
        binding.notesRecyclerView.adapter = adapter

        // 3. Le decimos al RecyclerView cómo posicionar los items (lista vertical)
        binding.notesRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    // --- Lógica principal de Reproducción ---
    private fun onNotePlay(note: VoiceNote) {
        val oldPlayingId = currentlyPlayingId
        val oldPosition = adapter.getPositionById(oldPlayingId) // <-- CORREGIDO

        if (mediaPlayer != null && currentlyPlayingId == note.id) {
            // --- Caso 1: Se pulsa "Stop" en la nota que está sonando ---
            stopPlayback()
        } else {
            // --- Caso 2: Se pulsa "Play" en una nota nueva ---
            stopPlayback() // Detenemos la anterior (si la hay)
            startPlayback(note)
        }

        // --- Actualizamos la UI ---
        // Le decimos al adaptador cuál es el nuevo ítem sonando (o ninguno)
       adapter.currentlyPlayingId = currentlyPlayingId

        // Notificamos al adaptador que debe redibujar la fila que *antes*
        // se estaba reproduciendo (para que vuelva a mostrar 'Play')
        if (oldPosition != -1) {
            adapter.notifyItemChanged(oldPosition)
        }

        // Notificamos para redibujar la fila *actual* (para mostrar 'Stop' o 'Play')
        val newPosition = adapter.getPositionById(note.id) // Pasamos el id de la nota actual
        if (newPosition != -1) {
            adapter.notifyItemChanged(newPosition)
        }
    }

    private fun startPlayback(note: VoiceNote) {
        // Construimos la ruta completa del archivo, igual que en AddNoteActivity
        val filePath = "${externalCacheDir?.absolutePath}/${note.audioFilePath}"

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(filePath)
                prepare() // Preparamos el audio
                start()   // ¡Comienza la reproducción!

                // Guardamos el ID de la nota que está sonando
                currentlyPlayingId = note.id

                // Muy importante: ¿Qué pasa cuando el audio termina?
                setOnCompletionListener {
                    // El audio ha terminado, así que limpiamos todo
                    val finishedPosition = adapter.getPositionById(note.id)
                    stopPlayback() // Limpia el media player y 'currentlyPlayingId'
                    adapter.currentlyPlayingId = null

                    // Actualiza el icono de la fila que acaba de terminar
                    if (finishedPosition != -1) {
                        adapter.notifyItemChanged(finishedPosition)
                    }
                }

                setOnErrorListener { _, _, _ ->
                    Toast.makeText(this@MainActivity, "Error al reproducir el audio", Toast.LENGTH_SHORT).show()
                    stopPlayback()
                    true // Indicamos que hemos manejado el error
                }

            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "No se encontró el archivo de audio", Toast.LENGTH_SHORT).show()
                stopPlayback()
            }
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentlyPlayingId = null
    }

    // Finalmente, añade esto para limpiar el reproductor si el usuario sale de la app
    override fun onStop() {
        super.onStop()
        // Si el usuario sale de la app, detenemos la reproducción
        // para que no se quede sonando en segundo plano.
        stopPlayback()
    }
}

