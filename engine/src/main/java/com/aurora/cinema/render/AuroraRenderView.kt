package com.aurora.cinema.render

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

class AuroraRenderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    val renderEngine: RenderEngine = RenderEngine(),
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    init {
        holder.addCallback(this)
        keepScreenOn = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        renderEngine.attachSurface(holder.surface, width, height)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        renderEngine.resize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        renderEngine.detachSurface(holder.surface)
    }

    fun release() {
        holder.removeCallback(this)
        renderEngine.release()
    }
}
