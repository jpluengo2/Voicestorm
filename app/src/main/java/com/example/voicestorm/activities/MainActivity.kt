package com.example.voicestorm.activities

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.voicestorm.R
import android.content.Intent
import com.google.android.material.floatingactionbutton.FloatingActionButton
// Importa la clase de View Binding generada para tu layout
import com.example.voicestorm.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    // 1. Declara una variable para el binding a nivel de clase
    // 'lateinit' indica que la inicializarás más tarde, antes de usarla.
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. Infla el layout usando View Binding
        // Esto reemplaza a setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /*
        // Nota: Ya no necesitas enableEdgeToEdge() ni el listener de WindowInsets
        // porque el 'CoordinatorLayout' o 'ConstraintLayout' en los layouts modernos
        // junto con 'fitsSystemWindows="true"' suelen manejar esto bien.
        // Si tu layout se ve mal sin ello, puedes volver a añadirlo.
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        */

        // 3. Configura el listener del FAB usando el objeto de binding
        // ¡Mucho más limpio y seguro! No necesitas 'findViewById'.
        binding.fabAddNote.setOnClickListener {
            val intent = Intent(this, AddNoteActivity::class.java)
            startActivity(intent)
        }
    }
}