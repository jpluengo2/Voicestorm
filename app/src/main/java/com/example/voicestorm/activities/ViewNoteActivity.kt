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

    // --- Lógica de Exportación (SAF) ---
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // --- Inflate layout using View Binding ---
        binding = ActivityViewNoteBinding.inflate(layoutInflater) // <-- INFLATE BINDING
        setContentView(binding.root) // <-- SET ROOT VIEW FROM BINDING

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
        Log.d("SETUP_CHECK", "setupClickListeners() completado.") // Log DESPUÉS de llamar

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
        Log.d("BIND_CHECK", "Initial UI State Set.")
    }

    private fun setupClickListeners() {
        Log.d("SETUP_CHECK", "Iniciando asignación de listeners...")
        // Use binding to access views
        binding.toolbarViewNote.setNavigationOnClickListener { finish() }
        binding.playButton.setOnClickListener { if (playerState == PlayerState.PAUSED) resumePlayback() else startPlayback() }
        binding.pauseButton.setOnClickListener { pausePlayback() }
        binding.stopButton.setOnClickListener { stopPlayback() }
        binding.viewNoteTranscribeButton.setOnClickListener { onTranscribeClicked() } // Correct ID used here
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

    private fun populateUi(note: VoiceNote) {
        val dateFormatter = SimpleDateFormat("dd 'de' MMMM 'de' yyyy, HH:mm", Locale.getDefault())
        // Use binding to access views
        binding.dateTextView.text = dateFormatter.format(Date(note.timestamp))
        binding.titleTextView.text = note.title.ifEmpty { "Nota de voz" }

        // Audio Path and Player Controls
        if (note.audioFilePath != null) {
            binding.audioFilePathTextView.text = "Audio: ${note.audioFilePath}"
            binding.audioFilePathTextView.visibility = View.VISIBLE
            setPlayerControlsEnabled(true)
        } else {
            binding.audioFilePathTextView.text = "Audio: No disponible"
            binding.audioFilePathTextView.visibility = View.VISIBLE
            setPlayerControlsEnabled(false)
        }

        // Transcription Path, Content and Button Visibility
        if (note.transcriptFilePath != null) {
            binding.transcriptPathTextView.text = "Texto: ${File(note.transcriptFilePath!!).name}"
            binding.transcriptPathTextView.visibility = View.VISIBLE
            loadTranscriptFromFile(note.transcriptFilePath!!)
            binding.viewNoteTranscribeButton.visibility = View.GONE // Hide transcribe if already exists
        } else {
            binding.transcriptTextView.text = getString(R.string.transcript_placeholder)
            binding.transcriptPathTextView.visibility = View.GONE
            binding.viewNoteTranscribeButton.visibility = View.VISIBLE // Show transcribe if not exists
        }

        // General Action Buttons State
        binding.deleteButton.isEnabled = true
        binding.exportButton.isEnabled = (note.audioFilePath != null)
        binding.saveButton.isEnabled = (newTranscriptFilePath != null) // Only enable if new transcript was generated
    }

    private fun loadTranscriptFromFile(filePath: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                val text = if (file.exists()) file.readText() else "Error: No se encontró el archivo de texto."
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

    // --- Lógica de Reproducción ---
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
                    e.printStackTrace()
                    Toast.makeText(this@ViewNoteActivity, "No se encontró el archivo de audio", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun pausePlayback() {
        mediaPlayer?.pause()
        playerState = PlayerState.PAUSED
        updatePlayerUI()
    }

    private fun resumePlayback() {
        mediaPlayer?.start()
        playerState = PlayerState.PLAYING
        updatePlayerUI()
    }

    private fun stopPlayback() {
        releaseMediaPlayer()
        playerState = PlayerState.IDLE
        updatePlayerUI()
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun updatePlayerUI() {
        // Use binding to access views
        when (playerState) {
            PlayerState.IDLE -> {
                binding.playButton.visibility = View.VISIBLE
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
                binding.pauseButton.visibility = View.GONE
                binding.stopButton.visibility = View.VISIBLE
            }
        }
    }

    private fun setPlayerControlsEnabled(isEnabled: Boolean) {
        // Use binding to access views
        binding.playButton.isEnabled = isEnabled
        binding.pauseButton.isEnabled = isEnabled
        binding.stopButton.isEnabled = isEnabled
    }

    // --- Lógica de Transcripción (Simulada - Use binding) ---
    private fun onTranscribeClicked() {
        currentNote?.audioFilePath?.let { audioPath ->
            binding.viewNoteTranscribeButton.isEnabled = false // Use binding
            binding.transcriptTextView.text = "Generando transcripción simulada..." // Use binding

            val loremIpsum = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat."
            val transcriptFileName = audioPath.replace(".mp3", ".txt")
            // Ensure externalCacheDir is not null (check added in onCreate)
            val transcriptFile = File(externalCacheDir, transcriptFileName)

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    transcriptFile.writeText(loremIpsum)
                    newTranscriptFilePath = transcriptFile.absolutePath

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ViewNoteActivity, "Transcripción simulada creada.", Toast.LENGTH_SHORT).show()
                        binding.transcriptTextView.text = loremIpsum // Use binding
                        // Update UI state based on the currentNote which now *conceptually* has a new transcript path
                        // Re-calling populateUi might reset newTranscriptFilePath if not handled carefully.
                        // Instead, let's just update the relevant button state directly.
                        binding.saveButton.isEnabled = true
                        binding.viewNoteTranscribeButton.visibility = View.GONE // Hide transcribe button after success
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ViewNoteActivity, "Error al guardar el archivo de texto.", Toast.LENGTH_SHORT).show()
                        binding.viewNoteTranscribeButton.isEnabled = true // Re-enable if failed
                    }
                }
            }
        } ?: run {
            Toast.makeText(this, "No se puede transcribir: falta información de la nota.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Lógica de Acciones (Guardar, Borrar, Exportar) ---

    // --- Lógica de Acciones (Guardar, Borrar, Exportar - Use binding) ---

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
                            noteToDelete.transcriptFilePath?.let { File(it).delete() }
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

    private fun onExportClicked() {
        currentNote?.audioFilePath?.let { audioFileName ->
            // Use the file name stored in the DB for launching SAF
            exportAudioLauncher.launch(audioFileName)
        } ?: run {
            Toast.makeText(this, "No hay archivo de audio para exportar.", Toast.LENGTH_SHORT).show()
        }
    }

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

    // --- Utilidades y Ciclo de Vida ---
    private fun getAudioFilePath(note: VoiceNote): String {
        // Ensure you handle the case where audioFilePath might be just the filename
        val fileName = note.audioFilePath ?: return "" // Return empty if null
        // Reconstruct the full path using externalCacheDir
        return "${externalCacheDir?.absolutePath}/$fileName"
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()
    }
}
