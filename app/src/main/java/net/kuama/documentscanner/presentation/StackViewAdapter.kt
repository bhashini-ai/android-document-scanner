package net.kuama.documentscanner.presentation

import android.widget.ImageView
import com.squareup.picasso.Picasso
import net.kuama.documentscanner.databinding.ListItemTakenPhotoBinding
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class StackViewAdapter : RecyclerView.Adapter<StackViewAdapter.VH>() {
    private var _imageUris = mutableListOf<Uri>()
    val imageUris: List<Uri>
        get() = _imageUris

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemTakenPhotoBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val imageUri = _imageUris[position]
        holder.bind(imageUri)
    }

    override fun getItemCount() = _imageUris.size

    fun addImageUris(vararg uris: Uri) {
        _imageUris.apply {
            clear()
            addAll(uris.toList())
        }
        notifyDataSetChanged()
    }

    inner class VH(private val binding: ListItemTakenPhotoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(uri: Uri) {
            binding.image.loadImageUri(uri)
            binding.counter.text = _imageUris.size.toString()
        }
    }

    fun ImageView.loadImageUri(uri: Uri) {
        Picasso.get()
            .load(uri)
            .fit()
            .centerCrop()
            .into(this)
    }
}
