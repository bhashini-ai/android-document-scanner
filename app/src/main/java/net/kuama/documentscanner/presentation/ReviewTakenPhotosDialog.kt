package net.kuama.documentscanner.presentation

import android.app.AlertDialog
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.RecyclerView
import net.kuama.documentscanner.databinding.DialogReviewPhotosBinding
import net.kuama.documentscanner.databinding.ListItemReviewedPhotoBinding
import net.kuama.documentscanner.extensions.loadImageUri

class ReviewTakenPhotosDialog : DialogFragment() {

    @Suppress("DEPRECATION")
    private val photoAdapter: ReviewPhotosAdapter by lazy {
        val uris = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU)
            requireArguments().getParcelableArrayList(EXTRA_PHOTOS)
        else requireArguments().getParcelableArrayList(EXTRA_PHOTOS, Uri::class.java)

        ReviewPhotosAdapter(
            uris ?: emptyList(),
            onLastPhotoDeleted = {
                dialog?.dismiss()
            },
            onDeletePhoto = { removedPhotoIndex ->
                setFragmentResult(
                    KEY_RESULT,
                    bundleOf(EXTRA_REMOVED_PHOTO_INDEX to removedPhotoIndex)
                )
            }
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        val binding = DialogReviewPhotosBinding.inflate(layoutInflater, null, false)
        return AlertDialog.Builder(requireContext())
            .setTitle("Review taken photos")
            .setView(binding.root)
            .setNegativeButton("ok") { dialog, _ -> dialog?.dismiss() }
            .create().also {
                isCancelable = false
                it.setOnShowListener {
                    binding.photos.adapter = photoAdapter
                }
            }
    }

    companion object {
        private const val EXTRA_PHOTOS = "photoUrisExtra"
        private const val KEY_RESULT = "resultKey"
        private const val EXTRA_REMOVED_PHOTO_INDEX = "removedPhotoIndexExtra"

        fun show(
            act: FragmentActivity,
            takenPhotos: List<Uri>,
            onReceiveDeletedPhotoIndex: (Int) -> Unit
        ) {
            val dialog = ReviewTakenPhotosDialog()
            dialog.arguments = bundleOf(EXTRA_PHOTOS to takenPhotos)
            dialog.show(act.supportFragmentManager, ReviewTakenPhotosDialog::class.simpleName)
            act.supportFragmentManager.setFragmentResultListener(KEY_RESULT, act) { _, bundle ->
                val resultData = bundle.getInt(EXTRA_REMOVED_PHOTO_INDEX)
                onReceiveDeletedPhotoIndex(resultData)
            }
        }
    }

    class ReviewPhotosAdapter(
        data: List<Uri>,
        var onLastPhotoDeleted: () -> Unit,
        var onDeletePhoto: (Int) -> Unit
    ) :
        RecyclerView.Adapter<ViewHolder>() {
        val items = data.toMutableList()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ListItemReviewedPhotoBinding.inflate(inflater, parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item) { clickedItem ->
                val removedItemIndex = removeItem(clickedItem)
                onDeletePhoto.invoke(removedItemIndex)
            }
        }

        override fun getItemCount(): Int = items.size

        private fun removeItem(item: Uri): Int {
            val index = items.indexOf(item)
            items.remove(item)
            if (index != -1) notifyItemRemoved(index)
            if (items.size == 0) {
                onLastPhotoDeleted.invoke()
            }
            return index
        }
    }

    class ViewHolder(private val binding: ListItemReviewedPhotoBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(imageItem: Uri, onRemoveClicked: (Uri) -> Unit) {
            with(binding) {
                image.loadImageUri(imageItem)
                remove.setOnClickListener { onRemoveClicked(imageItem) }
            }
        }
    }
}
