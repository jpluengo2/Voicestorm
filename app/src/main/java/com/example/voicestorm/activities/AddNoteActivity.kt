package com.example.voicestorm.activities

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.voicestorm.R
import java.io.IOException

class AddNoteActivity : AppCompatActivity() {

    // --- Permisos ---
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Permiso concedido. ¡Ya puedes grabar!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permiso denegado. No se puede grabar sin acceso al micrófono.", Toast.LENGTH_LONG).show()
            }
        }

    // --- Vistas de la UI ---
    private lateinit var recordButton: ImageButton
    private lateinit var pauseButton: ImageButton
    private lateinit var stopButton: ImageButton
    private lateinit var audioFilePathTextView: TextView

    // --- Lógica de Grabación ---
    private enum class RecordingState {
        IDLE, RECORDING, PAUSED
    }
    private var recordingState: RecordingState = RecordingState.IDLE

    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_note)

        // 1. Enlazar los botones y vistas del layout
        recordButton = findViewById(R.id.recordButton)
        pauseButton = findViewById(R.id.pauseButton)
        stopButton = findViewById(R.id.stopButton)
        audioFilePathTextView = findViewById(R.id.audioFilePathTextView)

        // 2. Configurar los listeners de clics
        setupClickListeners()

        // 3. Solicitar permiso de micrófono al iniciar
        requestMicrophonePermission()

        // 4. Inicializar la UI
        updateUIForRecordingState()
    }

    private fun setupClickListeners() {
        recordButton.setOnClickListener {
            when (recordingState) {
                RecordingState.IDLE -> startRecording()
                RecordingState.PAUSED -> resumeRecording()
                else -> { /* No hacer nada */ }
            }
        }

        pauseButton.setOnClickListener {
            if (recordingState == RecordingState.RECORDING) {
                pauseRecording()
            }
        }

        stopButton.setOnClickListener {
            if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
                stopRecording()
            }
        }
    }

    // --- Funciones de Grabación ---

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicrophonePermission()
            return
        }

        try {
            // Creación del archivo de salida
            val fileName = "${System.currentTimeMillis()}_voicestorm.mp3"
            // Usamos el directorio de caché externo específico de la app
            audioFilePath = "${externalCacheDir?.absolutePath}/$fileName"

            // Configuración del MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFilePath)
                prepare()
                start()
            }

            recordingState = RecordingState.RECORDING
            updateUIForRecordingState()
            Toast.makeText(this, "Grabación iniciada", Toast.LENGTH_SHORT).show()

        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error al iniciar la grabación: ${e.message}", Toast.LENGTH_LONG).show()
            releaseMediaRecorder()
        }
    }

    private fun pauseRecording() {
        // La pausa solo está disponible en Android 7 (API 24) y superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.pause()
            recordingState = RecordingState.PAUSED
            updateUIForRecordingState()
            Toast.makeText(this, "Grabación pausada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resumeRecording() {
        // La reanudación solo está disponible en Android 7 (API 24) y superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.resume()
            recordingState = RecordingState.RECORDING
            updateUIForRecordingState()
            Toast.makeText(this, "Grabación reanudada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        releaseMediaRecorder()
        recordingState = RecordingState.IDLE
        updateUIForRecordingState()

        // Mostramos la ruta del archivo y preparamos para la transcripción
        audioFilePathTextView.text = "Archivo de audio: $audioFilePath"
        audioFilePathTextView.visibility = View.VISIBLE

        Toast.makeText(this, "Grabación finalizada.", Toast.LENGTH_SHORT).show()

        // TODO: Aquí llamaremos a la función de transcripción
        // transcribeAudio(audioFilePath)
    }

    private fun releaseMediaRecorder() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            e.printStackTrace()
            // Este catch es para evitar crashes si se llama a stop() en un estado incorrecto.
        }
    }

    // --- Gestión de la UI y Permisos ---

    private fun updateUIForRecordingState() {
        when (recordingState) {
            RecordingState.IDLE -> {
                recordButton.visibility = View.VISIBLE
                recordButton.setImageResource(R.drawable.ic_record)
                pauseButton.visibility = View.GONE
                stopButton.visibility = View.GONE
            }
            RecordingState.RECORDING -> {
                recordButton.visibility = View.GONE
                // El botón de pausa solo se muestra en versiones compatibles
                pauseButton.visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) View.VISIBLE else View.GONE
                stopButton.visibility = View.VISIBLE
            }
            RecordingState.PAUSED -> {
                recordButton.visibility = View.VISIBLE
                recordButton.setImageResource(R.drawable.ic_play_arrow) // Icono para reanudar
                pauseButton.visibility = View.GONE
                stopButton.visibility = View.VISIBLE
            }
        }
    }

    private fun requestMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // El permiso ya está concedido.
            }

            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                Toast.makeText(this, "Se necesita el permiso de micrófono para grabar notas de voz.", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Es muy importante liberar el MediaRecorder si la actividad se destruye
        // para evitar fugas de memoria y errores.
        releaseMediaRecorder()
    }
}


//***************** APUNTES SOBRE LA IMPLEMENTACIÓN ******************

/*
Cambios y Mejoras en este código:
1.Constructor de MediaRecorder:
Se ha actualizado para usar el constructor correcto según la versión de Android
(Build.VERSION.SDK_INT), evitando una advertencia de "deprecated".
2.Manejo de Pausa/Reanudar:
La pausa y reanudación (pause() y resume()) solo funcionan
en Android 7 (API 24) o superior. He añadido comprobaciones (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N))
para que la app no falle en versiones más antiguas. En esas versiones, el botón de pausa simplemente no aparecerá.
3.Función releaseMediaRecorder():
He creado una función específica para detener, resetear y liberar el MediaRecorder de forma segura.
Esto evita repetir código y ayuda a prevenir errores.
4.Metodo onDestroy(): Se ha sobreescrito el metodo onDestroy() para asegurar que, si el usuario cierra
la pantalla mientras está grabando, el MediaRecorder se libere correctamente, evitando que la app se quede "enganchada" o falle.
5.Manejo de IOException:
La preparación del MediaRecorder puede lanzar una IOException, por lo que se ha envuelto en un bloque try-catch
para mayor robustez.
 */
