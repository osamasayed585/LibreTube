package com.github.libretube.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.TimeBar
import androidx.media3.ui.TimeBar.OnScrubListener
import com.github.libretube.extensions.dpToPx
import kotlin.math.abs

@UnstableApi
open class DismissableTimeBar(
    context: Context,
    attributeSet: AttributeSet? = null
): DefaultTimeBar(context, attributeSet) {
    private var shouldAddListener = false
    var exoPlayer: Player? = null

    // Drag-only seeking state
    private var initialX: Float = 0f
    private var initialY: Float = 0f
    private var initialDownTime: Long = 0L
    private var waitingForDrag: Boolean = false
    private var dragStarted: Boolean = false
    private val touchSlopPx: Int = ViewConfiguration.get(context).scaledTouchSlop

    init {
        addSeekBarListener(object : OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) = Unit

            override fun onScrubMove(timeBar: TimeBar, position: Long) = Unit

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                if (canceled) return
                // Ignore if gesture started too far above the bar (keep original behavior)
                if (initialY <= MINIMUM_ACCEPTED_HEIGHT.dpToPx()) return
                exoPlayer?.seekTo(position)
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y
                initialDownTime = event.downTime
                waitingForDrag = true
                dragStarted = false
                // Consume without forwarding to prevent tap-to-seek or thumb jump
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (waitingForDrag) {
                    val dx = abs(event.x - initialX)
                    val dy = abs(event.y - initialY)
                    if (dx > touchSlopPx || dy > touchSlopPx) {
                        // Begin scrubbing now by synthesizing a DOWN at the initial position
                        val fakeDown = MotionEvent.obtain(event)
                        fakeDown.action = MotionEvent.ACTION_DOWN
                        fakeDown.setLocation(initialX, initialY)
                        super.onTouchEvent(fakeDown)
                        fakeDown.recycle()

                        waitingForDrag = false
                        dragStarted = true
                    } else {
                        // Still not considered a drag; consume
                        return true
                    }
                }

                // Forward MOVE only after we've started dragging
                if (dragStarted) return super.onTouchEvent(event)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // If we started a drag, forward the terminal event to finish scrubbing
                if (dragStarted) {
                    val handled = super.onTouchEvent(event)
                    waitingForDrag = false
                    dragStarted = false
                    return handled
                }

                // It's a tap without drag: do nothing (prevent seek and thumb jump)
                waitingForDrag = false
                dragStarted = false
                // Optionally report click for accessibility without side effects
                performClick()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    /**
     * DO NOT CALL THIS METHOD DIRECTLY. Use [addSeekBarListener] instead!
     */
    override fun addListener(listener: OnScrubListener) {
        if (shouldAddListener) super.addListener(listener)
    }

    /**
     * Wrapper to circumvent adding the listener created by [PlayerControlView]
     */
    fun addSeekBarListener(listener: OnScrubListener) {
        shouldAddListener = true
        addListener(listener)
        shouldAddListener = false
    }

    fun setPlayer(player: Player) {
        this.exoPlayer = player
    }

    companion object {
        private const val MINIMUM_ACCEPTED_HEIGHT = -70f
    }
}