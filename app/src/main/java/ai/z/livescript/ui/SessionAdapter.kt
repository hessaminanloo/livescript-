package ai.z.livescript.ui

import ai.z.livescript.data.SessionEntity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ai.z.livescript.databinding.ItemSessionBinding
import java.text.SimpleDateFormat
import java.util.Locale

class SessionAdapter(
    private val onClick: (SessionEntity) -> Unit,
    private val onLongClickDelete: (SessionEntity) -> Unit
) : ListAdapter<SessionEntity, SessionAdapter.VH>(DIFF) {

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    inner class VH(val binding: ItemSessionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val modeLabel = if (item.wasOffline) "آفلاین" else "آنلاین"
        holder.binding.tvTitle.text = item.title
        holder.binding.tvMeta.text = "${dateFormat.format(item.createdAt)} · $modeLabel"
        holder.binding.tvPreview.text = item.transcript
        holder.binding.root.setOnClickListener { onClick(item) }
        holder.binding.root.setOnLongClickListener { onLongClickDelete(item); true }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SessionEntity>() {
            override fun areItemsTheSame(a: SessionEntity, b: SessionEntity) = a.id == b.id
            override fun areContentsTheSame(a: SessionEntity, b: SessionEntity) = a == b
        }
    }
}
