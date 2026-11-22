package com.sunuhwi.poe2dbkr

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.sunuhwi.poe2dbkr.databinding.ItemCatalogBinding

class CatalogAdapter(
    private val onOpen: (CatalogItem) -> Unit,
    private val onStar: (CatalogItem) -> Unit,
) : ListAdapter<CatalogItem, CatalogAdapter.ItemHolder>(Diff) {

    private val starred = HashSet<String>()

    fun submit(items: List<CatalogItem>, bookmarked: Set<String>) {
        starred.clear()
        starred.addAll(bookmarked)
        submitList(items)
    }

    fun updateStar(url: String, saved: Boolean) {
        if (saved) starred.add(url) else starred.remove(url)
        val index = currentList.indexOfFirst { it.url == url }
        if (index >= 0) notifyItemChanged(index, PAYLOAD_STAR)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        val binding = ItemCatalogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ItemHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        val item = currentList[position]
        holder.bind(item, starred.contains(item.url))
    }

    override fun onBindViewHolder(holder: ItemHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        val item = currentList[position]
        holder.renderStar(starred.contains(item.url))
    }

    inner class ItemHolder(private val binding: ItemCatalogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CatalogItem, saved: Boolean) {
            binding.tvName.text = item.name
            binding.tvNameEn.text = item.nameEn
            binding.tvNameEn.isVisible = item.nameEn.isNotBlank()
            binding.tvTags.text = item.tags.joinToString(" · ")
            binding.tvTags.isVisible = item.tags.isNotEmpty()
            binding.tvSummary.text = item.summary
            binding.tvSummary.isVisible = item.summary.isNotBlank()
            binding.tvMeta.text = item.meta
            binding.tvMeta.isVisible = item.meta.isNotBlank()

            binding.icon.load(item.icon.ifBlank { null }) {
                placeholder(R.drawable.bg_icon)
                error(R.drawable.bg_icon)
                crossfade(120)
            }

            binding.root.setOnClickListener { onOpen(item) }
            binding.star.setOnClickListener { onStar(item) }
            renderStar(saved)
        }

        fun renderStar(saved: Boolean) {
            binding.star.setImageResource(if (saved) R.drawable.ic_star else R.drawable.ic_star_border)
            binding.star.contentDescription = binding.root.context.getString(
                if (saved) R.string.bookmark_remove else R.string.bookmark_add
            )
        }
    }

    private object Diff : DiffUtil.ItemCallback<CatalogItem>() {
        override fun areItemsTheSame(oldItem: CatalogItem, newItem: CatalogItem): Boolean =
            oldItem.url == newItem.url

        override fun areContentsTheSame(oldItem: CatalogItem, newItem: CatalogItem): Boolean =
            oldItem == newItem
    }

    private companion object {
        val PAYLOAD_STAR = Any()
    }
}
