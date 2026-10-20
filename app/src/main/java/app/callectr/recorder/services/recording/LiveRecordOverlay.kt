/*
 * Callectr: live in-call Record control.
 */
package app.callectr.recorder.services.recording

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import app.callectr.recorder.utils.AppLogger

/**
 * Optional floating Record button shown over the active call UI.
 *
 * The notification action remains the reliable fallback. This overlay is only added when the user
 * explicitly granted "Display over other apps"; no permission means no error and no degraded recorder.
 */
object LiveRecordOverlay {

    enum class Owner { CARRIER, VOIP }

    private const val TAG = "CV:LiveRecordOverlay"
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var activeOwner: Owner? = null
    private var activeView: View? = null
    private var windowManager: WindowManager? = null

    fun isAllowed(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun showCarrier(context: Context) {
        show(context, Owner.CARRIER, "REC") {
            context.startService(
                Intent(context, RecordingForegroundService::class.java).apply {
                    action = RecordingForegroundService.ACTION_MANUAL_START
                }
            )
        }
    }

    fun showVoip(context: Context) {
        show(context, Owner.VOIP, "REC") {
            context.startService(
                Intent(context, DaemonKeepAliveService::class.java).apply {
                    action = DaemonKeepAliveService.ACTION_VOIP_RECORD_NOW
                }
            )
        }
    }

    fun dismiss(owner: Owner? = null) {
        main.post { dismissNow(owner) }
    }

    private fun show(context: Context, owner: Owner, label: String, onRecord: () -> Unit) {
        val appContext = context.applicationContext
        if (!Settings.canDrawOverlays(appContext)) {
            AppLogger.d(TAG, "Overlay permission not granted; notification Record action remains available")
            return
        }

        main.post {
            dismissNow(null)

            val wm = appContext.getSystemService(WindowManager::class.java)
            val density = appContext.resources.displayMetrics.density
            val pill = TextView(appContext).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(
                    (18 * density).toInt(),
                    (12 * density).toInt(),
                    (18 * density).toInt(),
                    (12 * density).toInt()
                )
                background = GradientDrawable().apply {
                    setColor(Color.rgb(196, 32, 48))
                    cornerRadius = 28 * density
                    setStroke((1 * density).toInt().coerceAtLeast(1), Color.argb(100, 255, 255, 255))
                }
                elevation = 10 * density
                contentDescription = "Record call"
                setOnClickListener {
                    runCatching { onRecord() }
                        .onFailure { AppLogger.w(TAG, "Record overlay action failed: ${it.message}") }
                    dismiss(owner)
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                x = (18 * density).toInt()
            }

            runCatching {
                wm.addView(pill, params)
                windowManager = wm
                activeView = pill
                activeOwner = owner
                AppLogger.i(TAG, "Floating Record button shown for $owner")
            }.onFailure {
                activeView = null
                activeOwner = null
                windowManager = null
                AppLogger.w(TAG, "Could not show floating Record button: ${it.message}")
            }
        }
    }

    private fun dismissNow(owner: Owner?) {
        if (owner != null && activeOwner != owner) return
        val view = activeView ?: return
        runCatching { windowManager?.removeViewImmediate(view) }
            .onFailure { AppLogger.d(TAG, "Removing overlay failed: ${it.message}") }
        activeView = null
        activeOwner = null
        windowManager = null
    }
}
