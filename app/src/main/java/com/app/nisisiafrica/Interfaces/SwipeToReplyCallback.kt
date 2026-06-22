package com.app.nisisiafrica.Interfaces

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.app.nisisiafrica.R

class SwipeToReplyCallback(
    context: Context,
    private val onSwiped: (Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private val replyIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_reply)
    private val iconMargin = 48
    private val swipeLimit = 0.3f
    private var triggered = false

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ) = false

    // Never actually complete the swipe — we handle it in onChildDraw
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 2f

    override fun getSwipeEscapeVelocity(defaultValue: Float) = Float.MAX_VALUE

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // never called due to threshold = 2f
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        triggered = false
        viewHolder.itemView.translationX = 0f
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) return

        val itemView = viewHolder.itemView
        val maxSwipe = itemView.width * swipeLimit
        // Swipe LEFT: dX is negative, clamp to [-maxSwipe, 0].
        val limitedDx = dX.coerceIn(-maxSwipe, 0f)
        val fraction = -limitedDx / maxSwipe

        // trigger callback once when threshold hit
        if (fraction >= 1f && !triggered) {
            triggered = true
            onSwiped(viewHolder.adapterPosition)
            itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }

        // draw reply icon on the right edge (revealed as the row slides left)
        replyIcon?.let {
            val iconSize = it.intrinsicHeight
            val iconTop = itemView.top + (itemView.height - iconSize) / 2
            val iconRight = itemView.right - iconMargin
            it.setBounds(iconRight - iconSize, iconTop, iconRight, iconTop + iconSize)
            it.alpha = (fraction * 255).toInt().coerceIn(0, 255)
            it.draw(c)
        }

        itemView.translationX = limitedDx
    }
}