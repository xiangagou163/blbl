package blbl.cat3399.feature.my

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.model.FollowedUgcCollection
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.databinding.ItemFavFolderBinding

class MyCollectionAdapter(
    private val onClick: (position: Int, collection: FollowedUgcCollection) -> Unit,
) : RecyclerView.Adapter<MyCollectionAdapter.Vh>() {
    private val items = ArrayList<FollowedUgcCollection>()

    init {
        setHasStableIds(true)
    }

    fun submit(list: List<FollowedUgcCollection>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = items[position].stableKey.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding =
            ItemFavFolderBinding.inflate(
                LayoutInflater.from(parent.context).cloneInUserScale(parent.context),
                parent,
                false,
            )
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class Vh(private val binding: ItemFavFolderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: FollowedUgcCollection,
            onClick: (position: Int, collection: FollowedUgcCollection) -> Unit,
        ) {
            val context = binding.root.context
            binding.tvTitle.text = item.title.ifBlank { context.getString(R.string.my_collection_untitled) }
            val ownerName = item.ownerName?.takeIf { it.isNotBlank() }
            val meta =
                when {
                    item.videoCount != null && ownerName != null ->
                        context.getString(R.string.my_collection_item_meta_fmt, ownerName, item.videoCount)
                    item.videoCount != null -> context.getString(R.string.my_collection_item_count_fmt, item.videoCount)
                    ownerName != null -> ownerName
                    else -> context.getString(R.string.my_collection_unknown_owner)
                }
            binding.tvCount.text = meta
            ImageLoader.loadInto(binding.ivCover, ImageUrl.cover(item.coverUrl))
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener
                onClick(pos, item)
            }
        }
    }
}