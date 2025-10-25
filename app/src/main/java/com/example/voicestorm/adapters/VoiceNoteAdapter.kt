package com.example.voicestorm.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.voicestorm.databinding.ItemVoiceNoteBinding
import com.example.voicestorm.data.VoiceNote
import java.text.SimpleDateFormat
import java.util.Locale

class VoiceNoteAdapter(private var notes: List<VoiceNote>) : RecyclerView.Adapter<VoiceNoteAdapter.VoiceNoteViewHolder>() {

    // El ViewHolder contiene las vistas de un único ítem del layout (item_voice_note.xml)
    inner class VoiceNoteViewHolder(val binding: ItemVoiceNoteBinding) : RecyclerView.ViewHolder(binding.root)

    // Se llama cuando el RecyclerView necesita crear un nuevo ViewHolder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoiceNoteViewHolder {
        // Infla el layout del ítem usando ViewBinding
        val binding = ItemVoiceNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VoiceNoteViewHolder(binding)
    }

    // Se llama para mostrar los datos en un ViewHolder específico.
    override fun onBindViewHolder(holder: VoiceNoteViewHolder, position: Int) {
        val note = notes[position]
        holder.binding.titleTextView.text = note.title

        // Formatear la fecha para mostrarla
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        holder.binding.dateTextView.text = sdf.format(note.timestamp)

        // Aquí puedes añadir listeners para el botón de play, etc.
        // holder.binding.playButton.setOnClickListener { /* Lógica de reproducción */ }
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
}
