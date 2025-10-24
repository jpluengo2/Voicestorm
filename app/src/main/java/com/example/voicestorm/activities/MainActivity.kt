package com.example.voicestorm.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.voicestorm.adapters.VoiceNoteAdapter
import com.example.voicestorm.databinding.ActivityMainBinding
import com.example.voicestorm.viewmodel.MainViewModel







class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 1. Inicializamos el ViewModel usando 'by viewModels()'
    // Esto lo crea y lo asocia automáticamente al ciclo de vida de esta Activity.
    private val voiceNoteViewModel: MainViewModel by viewModels()

    // 2. Declaramos nuestro adaptador
    private lateinit var adapter: VoiceNoteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflamos el binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configuramos la barra de herramientas (Toolbar)
        setSupportActionBar(binding.toolbarMain)

        // Configuramos el RecyclerView y el Adaptador
        setupRecyclerView()



        // 6. Observamos la "tubería" (el LiveData)
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
        // 1. Creamos una instancia de nuestro adaptador
        adapter = VoiceNoteAdapter(emptyList())

        // 2. Asignamos el adaptador al RecyclerView
        binding.notesRecyclerView.adapter = adapter

        // 3. Le decimos al RecyclerView cómo posicionar los items (lista vertical)
        binding.notesRecyclerView.layoutManager = LinearLayoutManager(this)
    }
}