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
import android.util.Log

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
    private lateinit var simulatorTranscriptionButton: Button
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

        // Imprimimos el ID que hemos recibido.
        Log.d("ID_Check", "[ViewNoteActivity] Recibido ID: $noteId")

        if (noteId == -1) {
            Toast.makeText(this, "Error: No se encontró la nota.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 2. Obtener instancia de la BD
        db = AppDataBase.getDatabase(this)

        // 3. Vincular todas las vistas
        bindViews()
        Log.d("SETUP_CHECK", "Llamando a setupClickListeners...") // Log ANTES de llamar


        // 4. Configurar listeners de clics
        setupClickListeners()
        Log.d("SETUP_CHECK", "setupClickListeners() completado.") // Log DESPUÉS de llamar


        // 5. Cargar los datos de la nota
        loadNoteData()



        Log.d("BIND_CHECK", "La función bindViews() se ha completado con éxito.")
    }

    private fun bindViews() {
        Log.d("BIND_CHECK", "Iniciando bindViews...")
        toolbar = findViewById(R.id.toolbar_view_note)
        Log.d("BIND_CHECK", "toolbar OK")
        dateTextView = findViewById(R.id.dateTextView)
        Log.d("BIND_CHECK", "dateTextView OK")
        titleTextView = findViewById(R.id.titleTextView)
        Log.d("BIND_CHECK", "titleTextView OK")
        playButton = findViewById(R.id.playButton)
        Log.d("BIND_CHECK", "playButton OK")
        pauseButton = findViewById(R.id.pauseButton)
        Log.d("BIND_CHECK", "pauseButton OK")
        stopButton = findViewById(R.id.stopButton)
        Log.d("BIND_CHECK", "stopButton OK")
        audioFilePathTextView = findViewById(R.id.audioFilePathTextView)
        Log.d("BIND_CHECK", "audioFilePathTextView OK")
        simulatorTranscriptionButton = findViewById(R.id.view_note_transcribe_button)
        Log.d("BIND_CHECK", "transcribeButton OK")
        transcriptPathTextView = findViewById(R.id.transcriptPathTextView)
        Log.d("BIND_CHECK", "transcriptPathTextView OK")
        transcriptTextView = findViewById(R.id.transcriptTextView)
        Log.d("BIND_CHECK", "transcriptTextView OK")
        deleteButton = findViewById(R.id.deleteButton)
        Log.d("BIND_CHECK", "deleteButton OK")
        exportButton = findViewById(R.id.exportButton)
        Log.d("BIND_CHECK", "exportButton OK")
        saveButton = findViewById(R.id.saveButton)
        Log.d("BIND_CHECK", "saveButton OK")
        Log.d("BIND_CHECK", "Todas las vistas vinculadas.")

        // Estado inicial (deshabilitado hasta que carguen los datos)
        setPlayerControlsEnabled(false)
        exportButton.isEnabled = false
        deleteButton.isEnabled = false
        saveButton.isEnabled = false
    }

    private fun setupClickListeners() {
        Log.d("SETUP_CHECK", "Iniciando asignación de listeners...")
        toolbar.setNavigationOnClickListener { finish() }
        playButton.setOnClickListener { if (playerState == PlayerState.PAUSED) resumePlayback() else startPlayback() }
        pauseButton.setOnClickListener { pausePlayback() }
        stopButton.setOnClickListener { stopPlayback() }
        simulatorTranscriptionButton.setOnClickListener { onTranscribeClicked() }
        deleteButton.setOnClickListener { onDeleteClicked() }
        exportButton.setOnClickListener { onExportClicked() }
        saveButton.setOnClickListener { onSaveClicked() }
        Log.d("SETUP_CHECK", "Todos los listeners han sido asignados.")
    }

    private fun loadNoteData() {
        // Iniciamos una corrutina en el scope del ciclo de vida de la Activity.
        lifecycleScope.launch { // No es necesario especificar Dispatchers.IO aquí.

            // Obtenemos la nota. El hilo se pausará aquí hasta que la BD devuelva una respuesta.
            // Usamos withContext para cambiar explícitamente al hilo de I/O para esta operación.
            val noteFromDb = withContext(Dispatchers.IO) {
                db.voiceNoteDao().getNoteById(noteId)
            }

            // En este punto, 'noteFromDb' ya tiene el valor (o es null si no se encontró).
            // El código vuelve automáticamente al hilo principal.

            // --- PRUEBA DE DEPURACIÓN #3 ---
            // Verificamos qué ha devuelto la base de datos.
            if (noteFromDb == null) {
                Log.e("ID_Check", "[ViewNoteActivity] ¡ERROR! La BD devolvió NULL para el ID: $noteId")
            } else {
                Log.d("ID_Check", "[ViewNoteActivity] La BD encontró la nota: $noteFromDb")
            }

            currentNote = noteFromDb // Asignamos el resultado a la variable de la clase.

            if (currentNote != null) {
                populateUi(currentNote!!)
            } else {
                // Si la nota es null (no se encontró por alguna razón), lo notificamos y cerramos.
                Toast.makeText(this@ViewNoteActivity, "Error: No se pudo cargar la nota desde la base de datos.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun populateUi(note: VoiceNote) {
        val dateFormatter = SimpleDateFormat("dd 'de' MMMM 'de' yyyy, HH:mm", Locale.getDefault())
        dateTextView.text = dateFormatter.format(Date(note.timestamp)) // Usa el timestamp de la nota

        titleTextView.text = note.title.ifEmpty { "Nota de voz" }

        // --- Lógica del Audio (Centralizada) ---
        if (note.audioFilePath != null) {
            audioFilePathTextView.text = "Audio: ${note.audioFilePath}"
            audioFilePathTextView.visibility = View.VISIBLE
            setPlayerControlsEnabled(true) // Habilitamos controles de audio
        } else {
            audioFilePathTextView.text = "Audio: No disponible"
            audioFilePathTextView.visibility = View.VISIBLE
            setPlayerControlsEnabled(false) // Deshabilitamos controles de audio
        }

        // --- Lógica de la Transcripción (Centralizada) ---
        if (note.transcriptFilePath != null) {
            transcriptPathTextView.text = "Texto: ${File(note.transcriptFilePath!!).name}"
            transcriptPathTextView.visibility = View.VISIBLE
            loadTranscriptFromFile(note.transcriptFilePath!!)
            simulatorTranscriptionButton.visibility = View.GONE // Si ya hay transcripción, ocultamos el botón
        } else {
            transcriptTextView.text = getString(R.string.transcript_placeholder)
            transcriptPathTextView.visibility = View.GONE
            simulatorTranscriptionButton.visibility = View.VISIBLE // Si no hay, lo mostramos
        }

        // --- Lógica de Botones Generales (Centralizada) ---
        // El botón de borrar siempre está disponible si la nota existe.
        deleteButton.isEnabled = true
        // El botón de exportar depende de si hay audio.
        exportButton.isEnabled = (note.audioFilePath != null)
        // El botón de guardar solo se habilita si se genera una nueva transcripción.
        saveButton.isEnabled = (newTranscriptFilePath != null)
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
            simulatorTranscriptionButton.isEnabled = false
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
                        populateUi(currentNote!!) // Habilitará el botón "Guardar"
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ViewNoteActivity, "Error al guardar el archivo de texto.", Toast.LENGTH_SHORT).show()
                        simulatorTranscriptionButton.isEnabled = true // Rehabilitar si falla
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
                    populateUi(currentNote!!) // Actualiza la UI
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
