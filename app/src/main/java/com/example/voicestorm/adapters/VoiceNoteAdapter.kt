package com.example.voicestorm.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.voicestorm.R
import com.example.voicestorm.data.VoiceNote
import com.example.voicestorm.databinding.ItemVoiceNoteBinding
import java.text.SimpleDateFormat
import java.util.Locale

class VoiceNoteAdapter(
    private var notes: List<VoiceNote>,
    private val onPlayClicked: (VoiceNote) -> Unit,
    private val onNoteClicked: (VoiceNote) -> Unit
) : RecyclerView.Adapter<VoiceNoteAdapter.VoiceNoteViewHolder>() {

    // El ID de la nota que se está reproduciendo actualmente. La Activity lo actualizará.
    var currentlyPlayingId: Int? = null

    // El ViewHolder contiene las vistas de un único ítem y configura los listeners.
    // Usamos 'inner class' para que pueda acceder a los miembros del Adapter, como 'onNoteClicked'.
    inner class VoiceNoteViewHolder(val binding: ItemVoiceNoteBinding) : RecyclerView.ViewHolder(binding.root) {

        // El método 'bind' ahora es responsable de TODO lo relacionado con una nota específica.
        fun bind(note: VoiceNote) {
            // 1. Asignar los datos a las vistas
            binding.titleTextView.text = note.title
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.dateTextView.text = sdf.format(note.timestamp)

            // 2. Decidir qué icono mostrar
            if (note.id == currentlyPlayingId) {
                binding.playButton.setImageResource(R.drawable.ic_stop)
            } else {
                binding.playButton.setImageResource(R.drawable.ic_play_arrow)
            }

            // 3. Configurar los listeners de clics
            // Listener para toda la fila
            itemView.setOnClickListener {
                onNoteClicked(note)
            }
            // Listener específico para el botón de Play
            binding.playButton.setOnClickListener {
                onPlayClicked(note)
            }
        }
    }

    // Se llama cuando el RecyclerView necesita crear un nuevo ViewHolder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoiceNoteViewHolder {
        // Infla el layout del ítem usando ViewBinding
        val binding = ItemVoiceNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VoiceNoteViewHolder(binding)
    }

    // Se llama para mostrar los datos en un ViewHolder específico.
    override fun onBindViewHolder(holder: VoiceNoteViewHolder, position: Int) {
        val note = notes[position]
        // ¡La única responsabilidad de onBindViewHolder ahora es llamar a bind!
        holder.bind(note)
    }

    // Devuelve el número total de ítems en la lista.
    override fun getItemCount(): Int {
        return notes.size
    }

    // Función para actualizar la lista de notas desde fuera y notificar al adapter.
    fun updateNotes(newNotes: List<VoiceNote>) {
        notes = newNotes
        notifyDataSetChanged() // Refresca toda la lista
    }

    fun getPositionById(noteId: Int?): Int {
        if (noteId == null) return -1
        return notes.indexOfFirst { it.id == noteId }
    }
}
