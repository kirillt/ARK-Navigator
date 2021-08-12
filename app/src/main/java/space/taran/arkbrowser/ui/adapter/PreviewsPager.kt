package space.taran.arkbrowser.ui.adapter

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_image.view.*
import space.taran.arkbrowser.R
import space.taran.arkbrowser.mvp.presenter.adapter.PreviewsList
import space.taran.arkbrowser.mvp.view.item.PreviewItemViewHolder

class PreviewsPager(val presenter: PreviewsList) : RecyclerView.Adapter<PreviewItemViewHolder>() {

    override fun getItemCount() = presenter.getCount()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        PreviewItemViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_image,
                parent,
                false))

    override fun onBindViewHolder(holder: PreviewItemViewHolder, position: Int) {
        holder.pos = position
        presenter.bindView(holder)
        val gestureDetector = getGestureDetector(holder)
        holder.itemView.layout_root.setOnTouchListener { view, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP)
                view.performClick()
            gestureDetector.onTouchEvent(motionEvent)
            return@setOnTouchListener true
        }
    }

    fun removeItem(position: Int) {
        val items = presenter.items().toMutableList()
        items.removeAt(position)
        presenter.updateItems(items.toList())
        super.notifyItemRemoved(position)
    }

    private fun getGestureDetector(holder: PreviewItemViewHolder): GestureDetectorCompat {
        val listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent?): Boolean {
                return true
            }

            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                presenter.itemClicked(holder.pos)
                return true
            }

        }
        return GestureDetectorCompat(holder.itemView.context, listener)
    }
}