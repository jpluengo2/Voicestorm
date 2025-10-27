package com.example.voicestorm.activities

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.voicestorm.R
import com.example.voicestorm.data.AppDataBase
import com.example.voicestorm.data.VoiceNote
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
// Import the generated binding class for activity_view_note.xml
import com.example.voicestorm.databinding.ActivityViewNoteBinding

class ViewNoteActivity : AppCompatActivity() {

    // --- Constantes ---
    companion object {
        const val NOTE_ID_KEY = "NOTE_ID"
    }

    // --- View Binding ---
    // Declare the binding variable
    private lateinit var binding: ActivityViewNoteBinding

    // --- Lógica de Base de Datos ---
    private lateinit var db: AppDataBase
    private var currentNote: VoiceNote? = null
    private var noteId: Int = -1 // Las IDs de Room suelen ser Int o Long. Usaremos Int para coincidir con tu DAO.

    // --- Lógica de Reproducción ---
    private enum class PlayerState { IDLE, PLAYING, PAUSED }
    private var playerState = PlayerState.IDLE
    private var mediaPlayer: MediaPlayer? = null

    // --- Lógica de Transcripción Simulada ---
    private var newTranscriptFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // --- Inflate layout using View Binding ---
        binding = ActivityViewNoteBinding.inflate(layoutInflater) // <-- INFLATE BINDING
        setContentView(binding.root) // <-- SET ROOT VIEW FROM BINDING

        // Comprobación de externalCacheDir (recomendada)
        if (externalCacheDir == null) {
            Toast.makeText(this, "Error: Almacenamiento externo no disponible.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 1. Obtener el ID de la nota
        // Usamos getIntExtra si el ID es Int
        // Imprimimos el ID que hemos recibido.
        noteId = intent.getIntExtra(NOTE_ID_KEY, -1)
        Log.d("ID_Check", "[ViewNoteActivity] Recibido ID: $noteId")
        if (noteId == -1) {
            Toast.makeText(this, "Error: No se encontró la nota.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 2. Obtener instancia de la BD
        db = AppDataBase.getDatabase(this)

        // 3. (Optional) Initialize views - bindViews() is no longer needed
        //    We access views directly via binding.xxx
        initializeUIState()

        // 4. Configurar listeners de clics
        setupClickListeners()

        // 5. Cargar los datos de la nota
        loadNoteData()
    }

    // This function replaces the need for bindViews() for initial setup
    private fun initializeUIState() {
        Log.d("BIND_CHECK", "Initializing UI State...")
        setPlayerControlsEnabled(false)
        binding.exportButton.isEnabled = false
        binding.deleteButton.isEnabled = false
        binding.saveButton.isEnabled = false
        binding.viewNoteTranscribeButton.visibility = View.GONE // Initially hide transcribe button
        binding.viewNoteTranscribeButton.isEnabled = false   // Deshabilitado inicialmente
        Log.d("BIND_CHECK", "Initial UI State Set.")
    }

    private fun setupClickListeners() {
        Log.d("SETUP_CHECK", "Iniciando asignación de listeners...")
        // Use binding to access views
        binding.toolbarViewNote.setNavigationOnClickListener { finish() }
        binding.playButton.setOnClickListener { if (playerState == PlayerState.PAUSED) resumePlayback() else startPlayback() }
        binding.pauseButton.setOnClickListener { pausePlayback() }
        binding.stopButton.setOnClickListener { stopPlayback() }
        binding.viewNoteTranscribeButton.setOnClickListener { onTranscribeClicked() }
        binding.deleteButton.setOnClickListener { onDeleteClicked() }
        binding.exportButton.setOnClickListener { onExportClicked() }
        binding.saveButton.setOnClickListener { onSaveClicked() }
        Log.d("SETUP_CHECK", "Todos los listeners han sido asignados.")
    }

    private fun loadNoteData() {
        lifecycleScope.launch {
            val noteFromDb = withContext(Dispatchers.IO) {
                db.voiceNoteDao().getNoteById(noteId)
            }
            Log.d("ID_Check", "[ViewNoteActivity] Nota desde BD: $noteFromDb")

            if (noteFromDb == null) {
                Log.e("ID_Check", "[ViewNoteActivity] ¡ERROR! La BD devolvió NULL para el ID: $noteId")
                Toast.makeText(this@ViewNoteActivity, "Error: No se pudo cargar la nota desde la base de datos.", Toast.LENGTH_LONG).show()
                finish()
            } else {
                Log.d("ID_Check", "[ViewNoteActivity] La BD encontró la nota: $noteFromDb")
                currentNote = noteFromDb
                populateUi(currentNote!!)
            }
        }
    }

    // Rellena la UI usando la variable 'binding'
    private fun populateUi(note: VoiceNote) {
        val dateFormatter = SimpleDateFormat("dd 'de' MMMM 'de' yyyy, HH:mm", Locale.getDefault())
        binding.dateTextView.text = dateFormatter.format(Date(note.timestamp))
        binding.titleTextView.text = note.title.ifEmpty { "Nota de voz" }

        val hasAudio = note.audioFilePath?.isNotEmpty() == true
        val hasTranscript = note.transcriptFilePath?.isNotEmpty() == true

        // 1. Configurar la sección de AUDIO
        if (hasAudio) {
            binding.audioFilePathTextView.text = "Audio: ${note.audioFilePath}"
            binding.audioFilePathTextView.visibility = View.VISIBLE
            setPlayerControlsEnabled(true) // Habilita Play/Pause/Stop
        } else {
            binding.audioFilePathTextView.text = "Audio: No disponible"
            binding.audioFilePathTextView.visibility = View.VISIBLE
            setPlayerControlsEnabled(false) // Deshabilita Play/Pause/Stop
        }

        // 2. Configurar la sección de TRANSCRIPCIÓN
        if (hasTranscript) {
            // Si YA HAY transcripción...
            binding.transcriptPathTextView.text = "Texto: ${note.transcriptFilePath}"
            binding.transcriptPathTextView.visibility = View.VISIBLE
            // Cargamos el contenido del archivo
            loadTranscriptFromFile(getTranscriptFullPath(note.transcriptFilePath!!))
            // Ocultamos el botón "Transcribir" porque ya no es necesario
            binding.viewNoteTranscribeButton.visibility = View.GONE
        } else {
            // Si NO HAY transcripción...
            binding.transcriptTextView.text = getString(R.string.transcript_placeholder)
            binding.transcriptPathTextView.visibility = View.GONE
            // El botón "Transcribir" solo debe ser visible y funcional si hay audio para transcribir
            if (hasAudio) {
                // --- INICIO DE LA CORRECCIÓN ---
                binding.viewNoteTranscribeButton.visibility = View.VISIBLE
                binding.viewNoteTranscribeButton.isEnabled = true // ¡¡ESTA ES LA LÍNEA CLAVE!!
                // --- FIN DE LA CORRECCIÓN ---
            } else {
                binding.viewNoteTranscribeButton.visibility = View.GONE
                binding.viewNoteTranscribeButton.isEnabled = false
            }
        }

        // 3. Configurar el estado de los BOTONES DE ACCIÓN
        binding.deleteButton.isEnabled = true
        binding.exportButton.isEnabled = hasAudio // Exportar solo es posible si hay audio
        // El botón guardar solo se habilita si se genera una NUEVA transcripción en esta pantalla.
        // Lo gestionaremos en onTranscribeClicked() y onSaveClicked(), aquí lo dejamos deshabilitado por defecto.
        binding.saveButton.isEnabled = (newTranscriptFilePath != null)

    }


    private fun loadTranscriptFromFile(filePath: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                val text = if (file.exists()) file.readText() else "Transcripción no realizada todavía."
                withContext(Dispatchers.Main) {
                    binding.transcriptTextView.text = text // Use binding
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    binding.transcriptTextView.text = "Error al leer la transcripción: ${e.message}" // Use binding
                }
            }
        }
    }

    // --- Lógica de Reproducción (usa 'binding' en updatePlayerUI) ---
    private fun startPlayback() {
        currentNote?.let {
            mediaPlayer = MediaPlayer().apply {
                try {
                    setDataSource(getAudioFilePath(it))
                    prepare()
                    start()
                    playerState = PlayerState.PLAYING
                    updatePlayerUI()
                    setOnCompletionListener { stopPlayback() }
                } catch (e: IOException) {
                    Log.e("PlaybackError", "Error al iniciar reproducción", e)
                    Toast.makeText(this@ViewNoteActivity, "Error al reproducir: ${e.message}", Toast.LENGTH_SHORT).show()
                    stopPlayback() // Asegurarse de limpiar si falla la preparación/inicio
                } catch (e: IllegalStateException) {
                    Log.e("PlaybackError", "Estado ilegal al iniciar reproducción", e)
                    Toast.makeText(this@ViewNoteActivity, "Error interno del reproductor.", Toast.LENGTH_SHORT).show()
                    stopPlayback()
                }
            }
        }
    }

    private fun pausePlayback() {
        if (mediaPlayer?.isPlaying == true) {
            try {
                mediaPlayer?.pause()
                playerState = PlayerState.PAUSED
                updatePlayerUI()
            } catch (e: IllegalStateException) {
                Log.e("PlaybackError", "Estado ilegal al pausar", e)
                stopPlayback() // Si falla la pausa, mejor parar
            }
        }
    }

    private fun resumePlayback() {
        if (mediaPlayer != null && playerState == PlayerState.PAUSED) {
            try {
                mediaPlayer?.start()
                playerState = PlayerState.PLAYING
                updatePlayerUI()
            } catch (e: IllegalStateException) {
                Log.e("PlaybackError", "Estado ilegal al reanudar", e)
                stopPlayback() // Si falla la reanudación, mejor parar
            }
        }
    }

    private fun stopPlayback() {
        releaseMediaPlayer()
        playerState = PlayerState.IDLE
        updatePlayerUI()
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset() // Añadir reset puede ayudar a limpiar estados internos
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Capturar cualquier excepción durante la liberación para no crashear
            Log.e("PlaybackError", "Error al liberar MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }
    }

    // Actualiza la UI de los controles de reproducción usando 'binding'
    private fun updatePlayerUI() {
        when (playerState) {
            PlayerState.IDLE -> {
                binding.playButton.visibility = View.VISIBLE
                binding.playButton.setImageResource(R.drawable.ic_play_arrow) // Asegurar icono correcto
                binding.pauseButton.visibility = View.GONE
                binding.stopButton.visibility = View.GONE
            }
            PlayerState.PLAYING -> {
                binding.playButton.visibility = View.GONE
                binding.pauseButton.visibility = View.VISIBLE
                binding.stopButton.visibility = View.VISIBLE
            }
            PlayerState.PAUSED -> {
                binding.playButton.visibility = View.VISIBLE
                binding.playButton.setImageResource(R.drawable.ic_play_arrow) // Icono para reanudar
                binding.pauseButton.visibility = View.GONE
                binding.stopButton.visibility = View.VISIBLE
            }
        }
    }

    // Habilita/deshabilita controles de reproducción usando 'binding'
    private fun setPlayerControlsEnabled(isEnabled: Boolean) {
        binding.playButton.isEnabled = isEnabled
        binding.pauseButton.isEnabled = isEnabled
        binding.stopButton.isEnabled = isEnabled
    }

    // --- Lógica de Transcripción (Simulada - usa 'binding') ---
    private fun onTranscribeClicked() {
        currentNote?.audioFilePath?.let { audioFileName -> // Usar el nombre de archivo guardado
            val audioPath = getAudioFilePath(currentNote!!) // Construir ruta completa
            binding.viewNoteTranscribeButton.isEnabled = false
            binding.transcriptTextView.text = "Generando transcripción simulada..."

            val loremIpsum = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat."
            val transcriptFileName = audioFileName.replace(".mp3", ".txt")
            val transcriptFile = File(externalCacheDir, transcriptFileName) // Usar externalCacheDir garantizado no nulo

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    transcriptFile.writeText(loremIpsum)
                    newTranscriptFilePath = transcriptFile.name

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ViewNoteActivity, "Transcripción simulada creada.", Toast.LENGTH_SHORT).show()
                        binding.transcriptTextView.text = loremIpsum
                        // Habilitar botón guardar y ocultar transcribir
                        binding.saveButton.isEnabled = true
                        binding.viewNoteTranscribeButton.visibility = View.GONE
                    }
                } catch (e: IOException) {
                    Log.e("TranscriptionError", "Error al guardar archivo de texto", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ViewNoteActivity, "Error al guardar transcripción.", Toast.LENGTH_SHORT).show()
                        binding.viewNoteTranscribeButton.isEnabled = true // Rehabilitar si falla
                    }
                }
            }
        } ?: run {
            Toast.makeText(this, "No se puede transcribir: falta la ruta del audio.", Toast.LENGTH_SHORT).show()
            binding.viewNoteTranscribeButton.isEnabled = true // Asegurar que se rehabilita si falta la ruta
        }
    }

    // --- Lógica de Acciones (Guardar, Borrar, Exportar) ---

    private fun onDeleteClicked() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_dialog_title)
            .setMessage(R.string.delete_dialog_message)
            .setPositiveButton(R.string.delete_dialog_positive) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    currentNote?.let { noteToDelete -> // Use a stable reference
                        try {
                            // Attempt to delete files only if paths are not null
                            noteToDelete.audioFilePath?.let { File(getAudioFilePath(noteToDelete)).delete() }
                            noteToDelete.transcriptFilePath?.let { fileName ->
                                File(getTranscriptFullPath(fileName)).delete() // <-- Reconstruir ruta
                            }
                        } catch (e: Exception) {
                            Log.e("DeleteError", "Error deleting files for note ID ${noteToDelete.id}", e)
                        }
                        // Delete from DB regardless of file deletion success/failure
                        db.voiceNoteDao().delete(noteToDelete)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ViewNoteActivity, "Nota borrada", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setNegativeButton(R.string.delete_dialog_negative, null)
            .create()
            .show()
    }

    private fun onSaveClicked() {
        // Use let for safety
        currentNote?.let { note ->
            newTranscriptFilePath?.let { newPath ->
                note.transcriptFilePath = newPath // Update the currentNote object

                lifecycleScope.launch(Dispatchers.IO) {
                    db.voiceNoteDao().update(note) // Save updated note to DB
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ViewNoteActivity, "Transcripción guardada", Toast.LENGTH_SHORT).show()
                        newTranscriptFilePath = null // Reset the flag
                        // Refresh the UI completely based on the updated note
                        populateUi(note) // This will now correctly show the transcript path and disable save button
                    }
                }
            }
        }
    }

    // --- Utilidades y Ciclo de Vida ---
    private fun getAudioFilePath(note: VoiceNote): String {
        // Ensure you handle the case where audioFilePath might be just the filename
        val fileName = note.audioFilePath ?: return "" // Return empty if null
        // Reconstruct the full path using externalCacheDir
        return "${externalCacheDir?.absolutePath}/$fileName"
    }

    // Nueva función para obtener la ruta completa del archivo de transcripción
    private fun getTranscriptFullPath(transcriptFileName: String): String {
        // Asume que transcriptFileName es solo el nombre del archivo
        return "${externalCacheDir!!.absolutePath}/$transcriptFileName"
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()
    }

    // LOGICA DE EXPORTACION PENDIENTE DE PROGRAMAR
    private fun onExportClicked() {
        Toast.makeText(this, "Exportación pendiente de reprogramar.", Toast.LENGTH_SHORT).show()
        /*
        currentNote?.audioFilePath?.let { audioFileName ->
            // Use the file name stored in the DB for launching SAF
            exportAudioLauncher.launch(audioFileName)
        } ?: run {
            Toast.makeText(this, "No hay archivo de audio para exportar.", Toast.LENGTH_SHORT).show()
        } */
    }

    /* De momento la exportación queda pendiente de reprogramar
    private fun copyFileToUri(sourceFilePath: String, destinationUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            try {
                val sourceFile = File(sourceFilePath)
                if (!sourceFile.exists()) {
                    Log.e("ExportError", "Source file does not exist: $sourceFilePath")
                    throw IOException("Source file not found")
                }
                val inputStream = FileInputStream(sourceFile)
                val outputStream = contentResolver.openOutputStream(destinationUri)

                inputStream.use { input ->
                    outputStream?.use { output ->
                        input.copyTo(output)
                        success = true // Mark as success only if copy completes
                    } ?: throw IOException("Could not open output stream for URI")
                }
            } catch (e: Exception) { // Catch broader exceptions
                Log.e("ExportError", "Error copying file $sourceFilePath to $destinationUri", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ViewNoteActivity, "Error al exportar archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                // Optional: You could show a success message here based on the 'success' flag if needed
                // Note: The second launcher (for text) is called automatically by the first one's result handler if audio export succeeds.
            }
        }
    }
    */

    /*
    private val exportAudioLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri ->
        uri?.let { destinationUri ->
            currentNote?.let {
                copyFileToUri(getAudioFilePath(it), destinationUri)
                // Después de exportar el audio, lanzamos el del texto
                it.audioFilePath?.replace(".mp3", ".txt")?.let { textFileName ->
                    exportTextLauncher.launch(textFileName)
                }
            }
        }
    }

    private val exportTextLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { destinationUri ->
            currentNote?.transcriptFilePath?.let { transcriptPath ->
                copyFileToUri(transcriptPath, destinationUri)
                Toast.makeText(this, "Archivos exportados", Toast.LENGTH_SHORT).show()
            } ?: run {
                // Caso borde: El usuario exporta audio pero no hay transcripción
                Toast.makeText(this, "Audio exportado. No hay transcripción para exportar.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    */

}
