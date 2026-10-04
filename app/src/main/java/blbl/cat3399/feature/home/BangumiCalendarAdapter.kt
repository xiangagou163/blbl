package blbl.cat3399.feature.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.bangumi.BangumiCalendarSubject
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.databinding.ItemBangumiCalendarBinding
import java.util.Locale

class BangumiCalendarAdapter(
    private val onClick: (position: Int, subject: BangumiCalendarSubject) -> Unit,
) : RecyclerView.Adapter<BangumiCalendarAdapter.ViewHolder>() {
    private val items = ArrayList<BangumiCalendarSubject>()

    init {
        setHasStableIds(true)
    }

    fun submit(subjects: List<BangumiCalendarSubject>) {
        items.clear()
        items.addAll(subjects)
        notifyDataSetChanged()
    }

    fun append(subjects: List<BangumiCalendarSubject>) {
        if (subjects.isEmpty()) return
        val start = items.size
        items.addAll(subjects)
        notifyItemRangeInserted(start, subjects.size)
    }

    override fun getItemId(position: Int): Long = items[position].id

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding =
            ItemBangumiCalendarBinding.inflate(
                LayoutInflater.from(parent.context).cloneInUserScale(parent.context),
                parent,
                false,
            )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        private val binding: ItemBangumiCalendarBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: BangumiCalendarSubject,
            onClick: (position: Int, subject: BangumiCalendarSubject) -> Unit,
        ) {
            binding.tvTitle.text = item.title

            val metadata =
                buildList {
                    item.ratingScore?.let {
                        add(binding.root.context.getString(R.string.bangumi_calendar_rating, String.format(Locale.ROOT, "%.1f", it)))
                    }
                    item.episodeCount?.let {
                        add(binding.root.context.getString(R.string.bangumi_calendar_episode_count, it))
                    }
                }.joinToString(" · ")
            binding.tvMetadata.text = metadata
            binding.tvMetadata.isVisible = metadata.isNotBlank()

            val tags = item.tags.take(4).joinToString(" · ")
            binding.tvTags.text = tags
            binding.tvTags.isVisible = tags.isNotBlank()

            ImageLoader.loadInto(binding.ivCover, item.coverUrl)
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener
                onClick(position, item)
            }
        }
    }
}
