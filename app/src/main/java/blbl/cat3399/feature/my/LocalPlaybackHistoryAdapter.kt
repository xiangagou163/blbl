package blbl.cat3399.feature.my

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.core.history.PlaybackHistoryRecord
import blbl.cat3399.databinding.ItemLocalPlaybackHistoryBinding
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal class LocalPlaybackHistoryAdapter(
    private val onOpen: (PlaybackHistoryRecord) -> Unit,
    private val onDelete: (PlaybackHistoryRecord, Int) -> Unit,
) : RecyclerView.Adapter<LocalPlaybackHistoryAdapter.ViewHolder>() {
    private val items = ArrayList<PlaybackHistoryRecord>()

    fun submit(records: List<PlaybackHistoryRecord>) {
        items.clear()
        items.addAll(records)
        notifyDataSetChanged()
    }

    fun requestFocusOpenAt(position: Int): Boolean {
        val holder = recyclerView?.findViewHolderForAdapterPosition(position) as? ViewHolder ?: return false
        return holder.binding.btnOpen.requestFocus()
    }

    private var recyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemLocalPlaybackHistoryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = items[position]
        holder.bind(record)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        val binding: ItemLocalPlaybackHistoryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: PlaybackHistoryRecord) {
            binding.tvTitle.text = record.title
            binding.tvProgress.text =
                "观看进度 ${formatDuration(record.progressMs)} / ${formatDuration(record.durationMs)}"
            binding.tvWatchedAt.text =
                "最近观看 ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.watchedAtMs))}"
            binding.btnOpen.contentDescription = "继续播放 ${record.title}"
            binding.btnDelete.contentDescription = "删除 ${record.title}"
            binding.btnOpen.setOnClickListener { onOpen(record) }
            binding.btnDelete.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onDelete(record, position)
            }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "--:--"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
