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
import com.example.voicestorm.data.AppDataBase // CORREGIDO: El nombre correcto es AppDataBase
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

class ViewNoteActivity : AppCompatActivity() {

    // --- Constantes ---
    companion object {
        const val NOTE_ID_KEY = "NOTE_ID"
    }

    // --- Vistas de la UI (Declaradas correctamente) ---
    private lateinit var toolbar: MaterialToolbar
    private lateinit var dateTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var playButton: ImageButton
    private lateinit var pauseButton: ImageButton
    private lateinit var stopButton: ImageButton
    private lateinit var audioFilePathTextView: TextView
    private lateinit var transcriptTextView: TextView
    private lateinit var transcriptPathTextView: TextView
    private lateinit var transcribeButton: Button
    private lateinit var deleteButton: Button
    private lateinit var exportButton: Button
    private lateinit var saveButton: Button

    // --- Lógica de Base de Datos ---
    private lateinit var db: AppDataBase
    private var currentNote: VoiceNote? = null
    private var noteId: Int = -1 // Las IDs de Room suelen ser Int o Long. Usaremos Int para coincidir con tu DAO.

    // --- Lógica de Reproducción ---
    private enum class PlayerState { IDLE, PLAYING, PAUSED }
    private var playerState = PlayerState.IDLE
    private var mediaPlayer: MediaPlayer? = null

    // --- Lógica de Transcripción Simulada ---
    private var newTranscriptFilePath: String? = null // Para guardar si transcribimos aquí

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
        setContentView(R.layout.activity_view_note)

        // 1. Obtener el ID de la nota
        noteId = intent.getIntExtra(NOTE_ID_KEY, -1) // Usamos getIntExtra si el ID es Int
        if (noteId == -1) {
            Toast.makeText(this, "Error: No se encontró la nota.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 2. Obtener instancia de la BD
        db = AppDataBase.getDatabase(this)

        // 3. Vincular todas las vistas
        bindViews()

        // 4. Configurar listeners de clics
        setupClickListeners()

        // 5. Cargar los datos de la nota
        loadNoteData()
    }

    private fun bindViews() {
        toolbar = findViewById(R.id.toolbar_view_note)
        dateTextView = findViewById(R.id.dateTextView)
        titleTextView = findViewById(R.id.titleTextView)
        playButton = findViewById(R.id.playButton)
        pauseButton = findViewById(R.id.pauseButton)
        stopButton = findViewById(R.id.stopButton)
        audioFilePathTextView = findViewById(R.id.audioFilePathTextView)
        transcriptTextView = findViewById(R.id.transcriptTextView)
        transcriptPathTextView = findViewById(R.id.transcriptPathTextView)
        transcribeButton = findViewById(R.id.transcriptionButton)
        deleteButton = findViewById(R.id.deleteButton)
        exportButton = findViewById(R.id.exportButton)
        saveButton = findViewById(R.id.saveButton)

        // Estado inicial (deshabilitado hasta que carguen los datos)
        setPlayerControlsEnabled(false)
        exportButton.isEnabled = false
        deleteButton.isEnabled = false
        saveButton.isEnabled = false
    }

    private fun setupClickListeners() {
        toolbar.setNavigationOnClickListener { finish() }
        playButton.setOnClickListener { if (playerState == PlayerState.PAUSED) resumePlayback() else startPlayback() }
        pauseButton.setOnClickListener { pausePlayback() }
        stopButton.setOnClickListener { stopPlayback() }
        transcribeButton.setOnClickListener { onTranscribeClicked() }
        deleteButton.setOnClickListener { onDeleteClicked() }
        exportButton.setOnClickListener { onExportClicked() }
        saveButton.setOnClickListener { onSaveClicked() }
    }

    private fun loadNoteData() {
        lifecycleScope.launch(Dispatchers.IO) {
            currentNote = db.voiceNoteDao().getNoteById(noteId)

            withContext(Dispatchers.Main) {
                currentNote?.let { populateUi(it) } ?: run {
                    Toast.makeText(this@ViewNoteActivity, "Error al cargar la nota.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun populateUi(note: VoiceNote) {
        val dateFormatter = SimpleDateFormat("dd 'de' MMMM 'de' yyyy, HH:mm", Locale.getDefault())
        dateTextView.text = dateFormatter.format(Date(note.timestamp)) // Usa el timestamp de la nota

        titleTextView.text = note.title.ifEmpty { "Nota de voz" }

        audioFilePathTextView.text = "Audio: ${note.audioFilePath}"
        audioFilePathTextView.visibility = View.VISIBLE

        if (note.transcriptFilePath != null) {
            transcriptPathTextView.text = "Texto: ${File(note.transcriptFilePath!!).name}"
            transcriptPathTextView.visibility = View.VISIBLE
            loadTranscriptFromFile(note.transcriptFilePath!!)
        } else {
            transcriptTextView.text = getString(R.string.transcript_placeholder)
        }

        updateButtonStates()
        setPlayerControlsEnabled(true)
        deleteButton.isEnabled = true
        exportButton.isEnabled = true
    }

    private fun loadTranscriptFromFile(filePath: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                val text = if (file.exists()) file.readText() else "Error: No se encontró el archivo de texto."
                withContext(Dispatchers.Main) {
                    transcriptTextView.text = text
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    transcriptTextView.text = "Error al leer la transcripción: ${e.message}"
                }
            }
        }
    }

    private fun updateButtonStates() {
        transcribeButton.visibility = if (currentNote?.transcriptFilePath == null) View.VISIBLE else View.GONE
        transcribeButton.isEnabled = (currentNote?.transcriptFilePath == null)
        saveButton.isEnabled = (newTranscriptFilePath != null)
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
        when (playerState) {
            PlayerState.IDLE -> {
                playButton.visibility = View.VISIBLE
                pauseButton.visibility = View.GONE
                stopButton.visibility = View.GONE
            }
            PlayerState.PLAYING -> {
                playButton.visibility = View.GONE
                pauseButton.visibility = View.VISIBLE
                stopButton.visibility = View.VISIBLE
            }
            PlayerState.PAUSED -> {
                playButton.visibility = View.VISIBLE
                pauseButton.visibility = View.GONE
                stopButton.visibility = View.VISIBLE
            }
        }
    }

    private fun setPlayerControlsEnabled(isEnabled: Boolean) {
        playButton.isEnabled = isEnabled
        pauseButton.isEnabled = isEnabled
        stopButton.isEnabled = isEnabled
    }

    // --- Lógica de Transcripción (Simulada) ---
    private fun onTranscribeClicked() {
        // Usamos 'let' para garantizar que currentNote y audioFilePath no son nulos
        currentNote?.audioFilePath?.let { audioPath ->
            transcribeButton.isEnabled = false
            transcriptTextView.text = "Generando transcripción simulada..."

            val loremIpsum = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat."

            // 'audioPath' aquí es garantizado no-nulo
            val transcriptFileName = audioPath.replace(".mp3", ".txt")
            val transcriptFile = File(externalCacheDir, transcriptFileName)

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    transcriptFile.writeText(loremIpsum)
                    newTranscriptFilePath = transcriptFile.absolutePath // Guardamos la ruta para el botón "Guardar"

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ViewNoteActivity, "Transcripción simulada creada.", Toast.LENGTH_SHORT).show()
                        transcriptTextView.text = loremIpsum
                        updateButtonStates() // Habilitará el botón "Guardar"
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ViewNoteActivity, "Error al guardar el archivo de texto.", Toast.LENGTH_SHORT).show()
                        transcribeButton.isEnabled = true // Rehabilitar si falla
                    }
                }
            }
        } ?: run {
            // Este bloque se ejecutaría si currentNote o audioFilePath son nulos
            Toast.makeText(this, "No se puede transcribir: falta información de la nota.", Toast.LENGTH_SHORT).show()
        }
    }



    // --- Lógica de Acciones (Guardar, Borrar, Exportar) ---

    private fun onDeleteClicked() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_dialog_title)
            .setMessage(R.string.delete_dialog_message)
            .setPositiveButton(R.string.delete_dialog_positive) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    currentNote?.let {
                        try {
                            File(getAudioFilePath(it)).delete()
                            it.transcriptFilePath?.let { path -> File(path).delete() }
                        } catch (e: Exception) { e.printStackTrace() }

                        db.voiceNoteDao().delete(it)
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
        if (currentNote != null && newTranscriptFilePath != null) {
            currentNote!!.transcriptFilePath = newTranscriptFilePath

            lifecycleScope.launch(Dispatchers.IO) {
                db.voiceNoteDao().update(currentNote!!)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ViewNoteActivity, "Transcripción guardada", Toast.LENGTH_SHORT).show()
                    newTranscriptFilePath = null // Reseteamos
                    updateButtonStates() // Actualiza la UI
                    transcriptPathTextView.text = "Texto: ${File(currentNote!!.transcriptFilePath!!).name}"
                    transcriptPathTextView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun onExportClicked() {
        currentNote?.audioFilePath?.let { audioFileName ->
            exportAudioLauncher.launch(audioFileName)
        }
    }


    private fun copyFileToUri(sourceFilePath: String, destinationUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = FileInputStream(File(sourceFilePath))
                val outputStream = contentResolver.openOutputStream(destinationUri)
                inputStream.use { input ->
                    outputStream?.use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ViewNoteActivity, "Error al exportar archivo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- Utilidades y Ciclo de Vida ---

    private fun getAudioFilePath(note: VoiceNote): String {
        return "${externalCacheDir?.absolutePath}/${note.audioFilePath}"
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()
    }
}
