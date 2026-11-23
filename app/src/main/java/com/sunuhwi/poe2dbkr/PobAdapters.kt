package com.sunuhwi.poe2dbkr

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.sunuhwi.poe2dbkr.databinding.ItemPobUniqueBinding

class PobUniqueAdapter(
    private val onClick: (CatalogItem) -> Unit,
) : ListAdapter<CatalogItem, PobUniqueAdapter.UniqueHolder>(Diff) {

    class UniqueHolder(val binding: ItemPobUniqueBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UniqueHolder {
        val binding = ItemPobUniqueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UniqueHolder(binding)
    }

    override fun onBindViewHolder(holder: UniqueHolder, position: Int) {
        val item = currentList[position]
        with(holder.binding) {
            tvUniqueName.text = item.name
            tvUniqueBase.text = item.nameEn.ifBlank { item.summary.ifBlank { item.catLabel } }
            icon.load(item.icon.ifBlank { null }) {
                placeholder(R.drawable.bg_icon)
                error(R.drawable.bg_icon)
                crossfade(80)
            }
            root.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<CatalogItem>() {
        override fun areItemsTheSame(oldItem: CatalogItem, newItem: CatalogItem) = oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: CatalogItem, newItem: CatalogItem) = oldItem == newItem
    }
}
