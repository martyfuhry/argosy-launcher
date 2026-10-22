package com.nendo.argosy.libretro

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.hideSystemBars

/**
 * The console's other screen, on the other display.
 *
 * The window this owns is created and destroyed by the display's own lifecycle, so the surface is
 * handed out and withdrawn through [onSurfaceChanged] rather than held: a surface that outlives
 * its window is drawn into forever without ever reaching the screen, which is what leaves a second
 * display frozen on its last frame after a suspend.
 *
 * The surface is ordered above its window and neither carries a background, because a SurfaceView
 * composites behind the window it lives in: an opaque background on either paints over the hole
 * the surface shows through, and the display stays black no matter what is drawn into it.
 *
 * It is also not cancelable, and never takes window focus. A Presentation is a dialog: Back would
 * otherwise dismiss it mid-game, and touching it would move focus off the emulator so the pad went
 * dead until the game's own display was tapped again. Not being focusable still delivers touches
 * inside its bounds; only the keys go elsewhere, which is where they belong.
 *
 * Built with [arrivesCovered] before the game's first frame, it opens black and fades the game in
 * on [reveal] over [ComponentDefaults.LaunchOverlay.arrivalMs], in step with the main screen's
 * GameArrival. No View can draw over a surface ordered on top, so the cover is the window itself:
 * a dim behind it holds the display black while the window's alpha, which its surface inherits,
 * rises from 0. The dim itself rises from 0 to full over [ComponentDefaults.LaunchOverlay.veilMs]
 * once the window shows, so the panel behind breathes out to black instead of cutting to it.
 * Built after the first frame, it shows the game at once.
 */
class SecondScreenPresentation(
    context: Context,
    display: Display,
    private val arrivesCovered: Boolean,
    private val onSurfaceChanged: (Surface?) -> Unit,
    private val onTouch: (x: Float, y: Float, width: Int, height: Int) -> Unit,
    private val onTouchReleased: () -> Unit
) : Presentation(context, display) {

    private lateinit var surfaceView: SurfaceView
    private var arrival: ValueAnimator? = null
    private var cover: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(false)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        if (arrivesCovered) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAlpha(0f, dimAmount = 0f)
        }

        surfaceView = SurfaceView(context).apply {
            setZOrderOnTop(true)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(surfaceView)
        window?.hideSystemBars()

        surfaceView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                    onTouch(event.x, event.y, view.width, view.height)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTouchReleased()
                else -> Unit
            }
            true
        }

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                onSurfaceChanged(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                onSurfaceChanged(holder.surface)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                onSurfaceChanged(null)
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (!arrivesCovered || cover != null || arrival != null) return
        cover = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ComponentDefaults.LaunchOverlay.veilMs.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener { setDimAmount(it.animatedValue as Float) }
            start()
        }
    }

    fun reveal() {
        if (!arrivesCovered || arrival != null || !isShowing) return
        arrival = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ComponentDefaults.LaunchOverlay.arrivalMs.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener { setWindowAlpha(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cover?.cancel()
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    setWindowAlpha(1f)
                }
            })
            start()
        }
    }

    private fun setDimAmount(dimAmount: Float) {
        val window = window ?: return
        window.attributes = window.attributes.also { params -> params.dimAmount = dimAmount }
    }

    private fun setWindowAlpha(alpha: Float, dimAmount: Float? = null) {
        val window = window ?: return
        window.attributes = window.attributes.also { params ->
            params.alpha = alpha
            if (dimAmount != null) params.dimAmount = dimAmount
        }
    }

    override fun onStop() {
        cover?.cancel()
        arrival?.cancel()
        onSurfaceChanged(null)
        super.onStop()
    }
}
