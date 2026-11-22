package com.sunuhwi.poe2dbkr

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.sunuhwi.poe2dbkr.databinding.ItemPobSlotBinding
import com.sunuhwi.poe2dbkr.databinding.ItemPobUniqueBinding

class PobSlotAdapter(
    private val onClick: (String) -> Unit,
) : ListAdapter<Pair<String, PobItem?>, PobSlotAdapter.SlotHolder>(Diff) {

    private var displayNames: Map<String, String> = Pob.SLOTS

    fun submit(slots: List<Pair<String, PobItem?>>, overrides: Set<String>) {
        overridden.clear()
        overridden.addAll(overrides)
        submitList(slots)
    }

    fun setDisplayNames(names: Map<String, String>) {
        displayNames = names
    }

    private val overridden = mutableSetOf<String>()

    class SlotHolder(val binding: ItemPobSlotBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotHolder {
        val binding = ItemPobSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SlotHolder(binding)
    }

    override fun onBindViewHolder(holder: SlotHolder, position: Int) {
        val (slot, item) = currentList[position]
        with(holder.binding) {
            tvSlot.text = displayNames[slot] ?: slot
            if (item == null) {
                tvItemName.text = root.context.getString(R.string.pob_empty_slot)
                tvItemName.setTextColor(ContextCompat.getColor(root.context, R.color.text_muted))
                tvItemLines.text = ""
                tvSlotBadge.visibility = android.view.View.GONE
            } else {
                tvItemName.text = item.displayName.ifBlank { item.name }
                tvItemName.setTextColor(rarityColor(root, item.rarity))
                val kind = item.itemClass.ifBlank {
                    if (item.weapon != WeaponVisualType.NONE) item.weapon.label else ""
                }
                val preview = item.lines.filter { line -> line != kind }.take(2).joinToString(" · ")
                tvItemLines.text = listOf(kind, preview).filter { it.isNotBlank() }.joinToString(" · ")
                tvSlotBadge.visibility =
                    if (slot in overridden) android.view.View.VISIBLE else android.view.View.GONE
            }
            root.setOnClickListener { onClick(slot) }
        }
    }

    companion object {
        fun rarityColor(view: android.view.View, rarity: String): Int = when (rarity) {
            "Unique" -> ContextCompat.getColor(view.context, R.color.unique_name)
            "Magic" -> ContextCompat.getColor(view.context, R.color.magic_name)
            "Rare" -> ContextCompat.getColor(view.context, R.color.rare_name)
            else -> ContextCompat.getColor(view.context, R.color.text_primary)
        }
    }

    private object Diff : DiffUtil.ItemCallback<Pair<String, PobItem?>>() {
        override fun areItemsTheSame(oldItem: Pair<String, PobItem?>, newItem: Pair<String, PobItem?>) =
            oldItem.first == newItem.first

        override fun areContentsTheSame(oldItem: Pair<String, PobItem?>, newItem: Pair<String, PobItem?>) =
            oldItem == newItem
    }
}

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
