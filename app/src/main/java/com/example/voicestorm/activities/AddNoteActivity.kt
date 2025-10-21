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
import java.io.File
//Importamos las clases necesarias de ML Kit (Speech, SpeechRecognizer, RecognitionOptions,
//RecognitionResult) y también Uri y File para manejar nuestro archivo de audio guardado.
import android.net.Uri // Necesario para manejar la ruta del archivo como Uri
import com.google.android.gms.tasks.Task // Para manejar las tareas asíncronas de ML Kit
import com.google.mlkit.speech.RecognitionListener // Opcional pero útil para feedback detallado
import com.google.mlkit.speech.RecognitionOptions // Para configurar el reconocedor
import com.google.mlkit.speech.RecognitionResult // El objeto que contendrá el texto reconocido
import com.google.mlkit.speech.Speech // Clase principal para obtener el reconocedor
import com.google.mlkit.speech.SpeechRecognizer // El objeto que hará la transcripción
import com.google.mlkit.speech.SpeechRecognitionError // Opcional, para errores detallados
import java.io.File // Para manejar el archivo de audio
import java.io.IOException // Para el manejo de errores al guardar el archivo de texto
import java.util.Locale // Para especificar el idioma (aunque usaremos la tag "es-ES")


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

    // --- Lógica de Traducción ----
    private lateinit var transcriptTextView: TextView
    private lateinit var transcriptPathTextView: TextView
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_note)

        // 1. Enlazar los botones y vistas del layout
        recordButton = findViewById(R.id.recordButton)
        pauseButton = findViewById(R.id.pauseButton)
        stopButton = findViewById(R.id.stopButton)
        audioFilePathTextView = findViewById(R.id.audioFilePathTextView)
        transcriptPathTextView = findViewById(R.id.transcriptPathTextView)

        // 2. Configurar los listeners de clics
        setupClickListeners()

        // 3. Solicitar permiso de micrófono al iniciar
        requestMicrophonePermission()

        // 4. Inicializar la UI
        updateUIForRecordingState()

        // 5. Inicializar el reconocedor de voz de ML Kit (¡NUEVA LLAMADA!)
        setupSpeechRecognizer()

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

    //Llamar a transcribeAudio al detener la grabación
    private fun stopRecording() {
        releaseMediaRecorder()
        recordingState = RecordingState.IDLE
        updateUIForRecordingState()

        // Mostramos la ruta del archivo y preparamos para la transcripción
        audioFilePathTextView = findViewById(R.id.audioFilePathTextView) // Asegúrate de enlazarla
        audioFilePathTextView.text = "Archivo de audio: $audioFilePath"
        audioFilePathTextView.visibility = View.VISIBLE

        Toast.makeText(this, "Grabación finalizada.", Toast.LENGTH_SHORT).show()

        // Llamamos a la función de transcripción (¡ASEGÚRATE DE QUE ESTÁ DESCOMENTADA!)
        if (audioFilePath.isNotEmpty()) { // Comprobación extra
            transcribeAudio(audioFilePath)
        } else {
            Log.w("AddNoteActivity", "audioFilePath está vacío, no se puede transcribir.")
            Toast.makeText(this, "No se encontró ruta de audio para transcribir.", Toast.LENGTH_SHORT).show()
        }
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

    //Liberar el SpeechRecognizer en onDestroy
    override fun onDestroy() {
        super.onDestroy()
        // Es muy importante liberar el MediaRecorder si la actividad se destruye
        // para evitar fugas de memoria y errores.
        releaseMediaRecorder()
        // ¡NUEVO! Liberamos el SpeechRecognizer de ML Kit
        try {
            speechRecognizer?.close() // Usamos close() para liberar recursos
            Log.d("AddNoteActivity", "SpeechRecognizer cerrado.")
        } catch (e: Exception) {
            Log.e("AddNoteActivity", "Error al cerrar SpeechRecognizer: ${e.message}", e)
        }
        speechRecognizer = null
    }

    private fun saveTranscriptToFile(text: String) {
        try {
            // Creamos un nombre de archivo .txt basado en el nombre del archivo de audio
            val transcriptFileName = audioFilePath.replace(".mp3", ".txt")
            val transcriptFile = File(transcriptFileName)

            // Escribimos el texto en el archivo
            transcriptFile.writeText(text)

            // Mostramos la ruta del archivo de texto en la UI
            transcriptPathTextView = findViewById(R.id.transcriptPathTextView) // Asegúrate de enlazarla
            transcriptPathTextView.text = "Archivo de texto: $transcriptFileName"
            transcriptPathTextView.visibility = View.VISIBLE
            Log.d("AddNoteActivity", "Transcripción guardada en: $transcriptFileName")

        } catch (e: IOException) {
            Log.e("AddNoteActivity", "Error al guardar el archivo de texto: ${e.message}", e)
            Toast.makeText(this, "Error al guardar el archivo de texto.", Toast.LENGTH_SHORT).show()
        }
    }

    // Inicializar el SpeechRecognizer
    //Necesitamos crear una instancia del reconocedor cuando la actividad se crea.
    private fun setupSpeechRecognizer() {
        try {
            // Opciones de reconocimiento:
            val options = RecognitionOptions.Builder()
                .setRecognitionMode(RecognitionOptions.OFFLINE) // Modo offline (usa el modelo descargado)
                .setSuppressPartialResults(true) // No queremos resultados parciales, solo el final
                .setLanguageTag("es-ES") // ¡Importante! Especificamos Español de España
                .build()

            // Obtenemos el cliente SpeechRecognizer
            speechRecognizer = Speech.createSpeechRecognizer(this, options)

            // (Opcional) Puedes añadir un listener para obtener más detalles del proceso
            /*
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onBeginningOfSpeech() { Log.d("MLKitSpeech", "onBeginningOfSpeech") }
                override fun onBufferReceived(buffer: ByteArray?) { Log.d("MLKitSpeech", "onBufferReceived") }
                override fun onEndOfSpeech() { Log.d("MLKitSpeech", "onEndOfSpeech") }
                override fun onError(error: Int, segment: Int) { Log.e("MLKitSpeech", "onError: $error") }
                override fun onResults(results: List<RecognitionResult>?, segment: Int) { Log.d("MLKitSpeech", "onResults") }
                override fun onSegmentResults(results: List<RecognitionResult>?, segment: Int) { Log.d("MLKitSpeech", "onSegmentResults") }
                override fun onReadyForSpeech() { Log.d("MLKitSpeech", "onReadyForSpeech") }
            })
            */
            Log.d("AddNoteActivity", "SpeechRecognizer inicializado correctamente.")

        } catch (e: Exception) {
            // Puede fallar si el modelo de lenguaje no está disponible o hay otro problema
            Log.e("AddNoteActivity", "Error al inicializar SpeechRecognizer: ${e.message}")
            Toast.makeText(this, "Error al configurar el reconocimiento de voz.", Toast.LENGTH_LONG).show()
            speechRecognizer = null // Aseguramos que sea null si falla
        }
    }

    //Implementar la función transcribeAudio
    //toma la ruta del archivo MP3, lo convierte a Uri y se lo pasa al speechRecognizer.
    private fun transcribeAudio(filePath: String) {
        if (speechRecognizer == null) {
            Toast.makeText(this, "El reconocedor de voz no está listo.", Toast.LENGTH_SHORT).show()
            // Podrías intentar reinicializarlo aquí si quieres: setupSpeechRecognizer()
            return
        }

        val audioFile = File(filePath)
        if (!audioFile.exists()) {
            Toast.makeText(this, "Error: No se encontró el archivo de audio.", Toast.LENGTH_SHORT).show()
            transcriptTextView.text = "Error: Archivo de audio no encontrado." // Actualiza UI
            return
        }

        // Mostramos un estado de "Transcribiendo..."
        transcriptTextView = findViewById(R.id.transcriptTextView) // Asegúrate de tenerla enlazada
        transcriptTextView.text = "Transcribiendo, por favor espera..."
        Toast.makeText(this, "Iniciando transcripción (ML Kit)...", Toast.LENGTH_SHORT).show()
        Log.d("AddNoteActivity", "Iniciando transcripción para: $filePath")


        // Convertimos la ruta del archivo a un objeto Uri
        val audioUri = Uri.fromFile(audioFile)

        // ¡La llamada clave a ML Kit!
        try {
            val recognitionTask: Task<RecognitionResult> = speechRecognizer!!.recognize(audioUri)

            recognitionTask
                .addOnSuccessListener { result ->
                    // ÉXITO: Tenemos el texto
                    val recognizedText = result.text ?: "No se pudo reconocer texto (resultado vacío)."
                    Log.d("AddNoteActivity", "Transcripción exitosa.")
                    transcriptTextView.text = recognizedText

                    // Guardamos el texto en un archivo .txt
                    saveTranscriptToFile(recognizedText)

                    Toast.makeText(this, "Transcripción completada.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    // ERROR: No se pudo transcribir
                    Log.e("AddNoteActivity", "Fallo en la transcripción: ${e.message}", e)
                    transcriptTextView.text = "Error al transcribir: ${e.localizedMessage}"
                    Toast.makeText(this, "Fallo en la transcripción.", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            // Error inesperado al llamar a recognize
            Log.e("AddNoteActivity", "Excepción al llamar a recognize(): ${e.message}", e)
            transcriptTextView.text = "Error inesperado al iniciar transcripción."
            Toast.makeText(this, "Error inesperado al transcribir.", Toast.LENGTH_SHORT).show()
        }
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

/*
El próximo paso, siguiendo nuestro plan, es uno de los más interesantes:
traducir la nota de voz a texto usando ML Kit de Google.
Cuando el usuario pulsa el botón "Stop" en la pantalla de AddNoteActivity,
ya hemos guardado el archivo de audio y mostramos su ruta.
Ahora, justo después de eso, iniciaremos el proceso de transcripción.

Para ello, necesitamos hacer lo siguiente:
1.Añadir las dependencias de ML Kit: Necesitamos la librería de reconocimiento de voz (speech-recognition).
2.Crear el reconocedor de voz: Configuraremos un objeto SpeechRecognizer que será el encargado de procesar el audio.
3.Implementar la función de transcripción: Crearemos una nueva función, transcribeAudio(filePath: String),
que tomará la ruta de nuestro archivo de audio, se la pasará al reconocedor y procesará el resultado (el texto).
4.Gestionar el resultado: Cuando ML Kit nos devuelva el texto, lo mostraremos en el TextView correspondiente
y lo guardaremos en un archivo de texto (.txt), tal y como planeamos.
5.Actualizar la UI: Mostraremos la ruta del nuevo archivo de texto y, si es necesario, algún mensaje de estado
(ej: "Transcribiendo...").
 */

/*
Resumen de la Implementación
1.Dependencia añadida: Tu proyecto ya incluye la capacidad de reconocer voz.
2.Lógica integrada: Al detener una grabación, se inicia automáticamente el proceso de transcripción.
3.Proceso de ML Kit:
•Le pasamos el archivo de audio (.mp3) a SpeechRecognizer.
•ML Kit lo procesa en el propio dispositivo (OFFLINE).
•Nos devuelve el resultado de forma asíncrona (a través de addOnSuccessListener y addOnFailureListener).
4.Gestión de archivos:
•Si la transcripción es exitosa, el texto se muestra en pantalla.
•Inmediatamente después, se crea un archivo .txt con el mismo nombre que el de audio.
•El contenido del TextView se guarda en ese archivo .txt.
•La ruta del nuevo archivo de texto se hace visible en la UI.
 */

/*
Puntos Fijos y Buenas Prácticas (Lo que está muy bien hecho)
1.Gestión de Estado Clara: El uso de la enum class RecordingState para controlar la UI (updateUIForRecordingState)
es una práctica excelente. Hace que el código sea legible, predecible y fácil de mantener.
2.Manejo de Ciclo de Vida (onDestroy): Liberar los recursos como MediaRecorder y SpeechRecognizer en onDestroy()
es crucial y lo has implementado perfectamente. Esto previene fugas de memoria y crashes, un error muy común
en este tipo de aplicaciones.
3.Permisos Modernos: Utilizas el ActivityResultLauncher (requestPermissionLauncher) para gestionar los permisos.
Esta es la forma moderna y recomendada por Google, mucho más limpia que el antiguo onRequestPermissionsResult.
4.Código Asíncrono con Listeners: El manejo de la tarea de ML Kit con .addOnSuccessListener y
.addOnFailureListener es correcto y demuestra que entiendes cómo trabajar con APIs que no devuelven resultados de inmediato.
5.Robustez y Manejo de Errores: Usas bloques try-catch en puntos críticos como la inicialización de MediaRecorder,
al llamar a stop() y al interactuar con el SpeechRecognizer. Esto hace tu app mucho más estable.
6.Separación de Responsabilidades: Las funciones como startRecording, stopRecording, setupSpeechRecognizer y
transcribeAudio tienen una única responsabilidad, lo que hace el código muy fácil de leer y depurar.
 */
