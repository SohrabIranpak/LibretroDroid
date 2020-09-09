/*
 *     Copyright (C) 2019  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.swordfish.libretrodroid

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.jakewharton.rxrelay2.BehaviorRelay
import com.swordfish.libretrodroid.gamepad.GamepadsManager
import io.reactivex.Observable
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRetroView(
    context: Context,
    private val coreFilePath: String,
    private val gameFilePath: String,
    private val systemDirectory: String = context.filesDir.absolutePath,
    private val savesDirectory: String = context.filesDir.absolutePath,
    private val saveRAMState: ByteArray? = null,
    private val shader: Int = LibretroDroid.SHADER_DEFAULT
) : AspectRatioGLSurfaceView(context), LifecycleObserver {

    private val openGLESVersion: Int

    private val retroGLEventsSubject = BehaviorRelay.create<GLRetroEvents>()

    private var gameLoaded: Boolean = false

    private var lifecycle: Lifecycle? = null

    init {
        openGLESVersion = getGLESVersion(context)
        preserveEGLContextOnPause = true
        setEGLContextClientVersion(openGLESVersion)
        setRenderer(Renderer())
        keepScreenOn = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    private fun onCreate(lifecycleOwner: LifecycleOwner) {
        lifecycle = lifecycleOwner.lifecycle
        Log.e("GLRetroView", "Starting LibretroDroid.create()")
        LibretroDroid.create(
            openGLESVersion,
            coreFilePath,
            systemDirectory,
            savesDirectory,
            shader,
            getScreenRefreshRate(),
            getDeviceLanguage()
        )
        Log.e("GLRetroView", "Completed LibretroDroid.create()")
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private fun onDestroy() {
        Log.e("GLRetroView", "Starting LibretroDroid.destroy()")
        LibretroDroid.destroy()
        Log.e("GLRetroView", "Completed LibretroDroid.destroy()")
        lifecycle = null
    }

    private fun getDeviceLanguage() = Locale.getDefault().language

    private fun getScreenRefreshRate(): Float {
        return (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate
    }

    fun sendKeyEvent(action: Int, keyCode: Int, port: Int = 0) {
        queueEvent { LibretroDroid.onKeyEvent(port, action, keyCode) }
    }

    fun sendMotionEvent(source: Int, xAxis: Float, yAxis: Float, port: Int = 0) {
        queueEvent { LibretroDroid.onMotionEvent(port, source, xAxis, yAxis) }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sendTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                sendTouchEvent(event)
            }
            MotionEvent.ACTION_UP -> {
                sendMotionEvent(MOTION_SOURCE_POINTER, -1f, -1f)
            }
        }
        return true
    }

    private fun sendTouchEvent(event: MotionEvent) {
        val x = event.x / width
        val y = event.y / height
        sendMotionEvent(MOTION_SOURCE_POINTER, x, y)
    }

    fun serializeState(): ByteArray {
        return LibretroDroid.serializeState()
    }

    fun unserializeState(data: ByteArray): Boolean {
        return LibretroDroid.unserializeState(data)
    }

    fun serializeSRAM(): ByteArray {
        return LibretroDroid.serializeSRAM()
    }

    fun reset() {
        LibretroDroid.reset()
    }

    fun getGLRetroEvents(): Observable<GLRetroEvents> {
        return retroGLEventsSubject
    }

    fun getVariables(): Array<Variable> {
        return LibretroDroid.getVariables()
    }

    fun updateVariables(vararg variables: Variable) {
        variables.forEach {
            LibretroDroid.updateVariable(it)
        }
    }

    fun getAvailableDisks() = LibretroDroid.availableDisks()
    fun getCurrentDisk() = LibretroDroid.currentDisk()
    fun changeDisk(index: Int) = LibretroDroid.changeDisk(index)

    private fun getGLESVersion(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return if (activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000) { 3 } else { 2 }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val mappedKey = GamepadsManager.getGamepadKeyEvent(keyCode)
        val port = (event?.device?.controllerNumber ?: 0) - 1

        if (event != null && port >= 0 && keyCode in GamepadsManager.GAMEPAD_KEYS) {
            sendKeyEvent(KeyEvent.ACTION_DOWN, mappedKey, port)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val mappedKey = GamepadsManager.getGamepadKeyEvent(keyCode)
        val port = (event?.device?.controllerNumber ?: 0) - 1

        if (event != null && port >= 0 && keyCode in GamepadsManager.GAMEPAD_KEYS) {
            sendKeyEvent(KeyEvent.ACTION_UP, mappedKey, port)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        val port = (event?.device?.controllerNumber ?: 0) - 1
        if (port >= 0) {
            when (event?.source) {
                InputDevice.SOURCE_JOYSTICK -> {
                    sendMotionEvent(
                        MOTION_SOURCE_DPAD,
                        event.getAxisValue(MotionEvent.AXIS_HAT_X),
                        event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                        port
                    )
                    sendMotionEvent(
                        MOTION_SOURCE_ANALOG_LEFT,
                        event.getAxisValue(MotionEvent.AXIS_X),
                        event.getAxisValue(MotionEvent.AXIS_Y),
                        port
                    )
                    sendMotionEvent(
                        MOTION_SOURCE_ANALOG_RIGHT,
                        event.getAxisValue(MotionEvent.AXIS_Z),
                        event.getAxisValue(MotionEvent.AXIS_RZ),
                        port
                    )
                }
            }
        }
        return super.onGenericMotionEvent(event)
    }

    // These functions are called only after the GLSurfaceView has been created.
    private inner class RenderLifecycleObserver : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        private fun resume() {
            Log.e("GLRetroView", "Starting LibretroDroid.resume()")
            LibretroDroid.resume()
            Log.e("GLRetroView", "Completed LibretroDroid.resume()")
            onResume()
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        private fun pause() {
            onPause()
            Log.e("GLRetroView", "Starting LibretroDroid.pause()")
            LibretroDroid.pause()
            Log.e("GLRetroView", "Completed LibretroDroid.pause()")
        }
    }

    inner class Renderer : GLSurfaceView.Renderer {
        override fun onDrawFrame(gl: GL10) {
            LibretroDroid.step()
            retroGLEventsSubject.accept(GLRetroEvents.FrameRendered)
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            Thread.currentThread().priority = Thread.MAX_PRIORITY
            Log.e("GLRetroView", "Started LibretroDroid.surfaceChanged()")
            LibretroDroid.onSurfaceChanged(width, height)
            Log.e("GLRetroView", "Completed LibretroDroid.surfaceChanged()")
        }

        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            Thread.currentThread().priority = Thread.MAX_PRIORITY
            initializeCore()
            retroGLEventsSubject.accept(GLRetroEvents.SurfaceCreated)
            //refreshAspectRatio()
        }
    }

    private fun refreshAspectRatio() {
        val aspectRatio = LibretroDroid.getAspectRatio()
        runOnUIThread {
            Log.e("GLRetroView", "Started setAspectRatio")
            setAspectRatio(aspectRatio)
            Log.e("GLRetroView", "Completed setAspectRatio")
        }
    }

    // These functions are called from the GL thread.
    private fun initializeCore() {
        if (gameLoaded) return
        Log.e("GLRetroView", "Started LibretroDroid.loadGame()")
        LibretroDroid.loadGame(gameFilePath)
        Log.e("GLRetroView", "Completed LibretroDroid.loadGame()")

        Log.e("GLRetroView", "Started LibretroDroid.unserializeSRAM()")
        saveRAMState?.let { LibretroDroid.unserializeSRAM(saveRAMState) }
        Log.e("GLRetroView", "Completed LibretroDroid.serializeSRAM()")

        Log.e("GLRetroView", "Started LibretroDroid.surfaceCreated()")
        LibretroDroid.onSurfaceCreated()
        Log.e("GLRetroView", "Completed LibretroDroid.surfaceCreated()")
        lifecycle?.addObserver(RenderLifecycleObserver())
        gameLoaded = true
    }

    private fun runOnUIThread(runnable: () -> Unit) {
        Handler(Looper.getMainLooper()).post(runnable)
    }

    sealed class GLRetroEvents {
        object FrameRendered: GLRetroEvents()
        object SurfaceCreated: GLRetroEvents()
    }

    companion object {
        const val MOTION_SOURCE_DPAD = LibretroDroid.MOTION_SOURCE_DPAD
        const val MOTION_SOURCE_ANALOG_LEFT = LibretroDroid.MOTION_SOURCE_ANALOG_LEFT
        const val MOTION_SOURCE_ANALOG_RIGHT = LibretroDroid.MOTION_SOURCE_ANALOG_RIGHT
        const val MOTION_SOURCE_POINTER = LibretroDroid.MOTION_SOURCE_POINTER

        const val SHADER_DEFAULT = LibretroDroid.SHADER_DEFAULT
        const val SHADER_CRT = LibretroDroid.SHADER_CRT
        const val SHADER_LCD = LibretroDroid.SHADER_LCD
        const val SHADER_SHARP = LibretroDroid.SHADER_SHARP
    }
}
