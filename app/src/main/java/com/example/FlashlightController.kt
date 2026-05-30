package com.example

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FlashlightController {
    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isStrobeActive = MutableStateFlow(false)
    val isStrobeActive: StateFlow<Boolean> = _isStrobeActive.asStateFlow()

    private val _isSosActive = MutableStateFlow(false)
    val isSosActive: StateFlow<Boolean> = _isSosActive.asStateFlow()

    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var isCallbackRegistered = false

    private val controllerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var activeJob: Job? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            super.onTorchModeChanged(id, enabled)
            if (id == cameraId) {
                _isTorchOn.value = enabled
                if (!enabled) {
                    // If turned off externally, ensure helper flags align
                    if (!_isStrobeActive.value && !_isSosActive.value) {
                        _isTorchOn.value = false
                    }
                }
            }
        }
    }

    fun initialize(context: Context) {
        if (cameraManager != null) return

        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        cameraManager = manager

        if (manager != null) {
            try {
                val list = manager.cameraIdList
                for (id in list) {
                    val characteristics = manager.getCameraCharacteristics(id)
                    val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraId = id
                        break
                    }
                }
                if (cameraId == null && list.isNotEmpty()) {
                    cameraId = list[0]
                }

                cameraId?.let { id ->
                    manager.registerTorchCallback(
                        torchCallback,
                        Handler(Looper.getMainLooper())
                    )
                    isCallbackRegistered = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Synchronized
    fun setTorch(enabled: Boolean) {
        cancelFlashingPatterns()
        setTorchRaw(enabled)
    }

    private fun setTorchRaw(enabled: Boolean) {
        val manager = cameraManager ?: return
        val id = cameraId ?: return
        try {
            manager.setTorchMode(id, enabled)
            _isTorchOn.value = enabled
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun toggleTorch() {
        if (_isStrobeActive.value || _isSosActive.value) {
            setTorch(true)
        } else {
            setTorch(!_isTorchOn.value)
        }
    }

    @Synchronized
    fun toggleStrobe() {
        val nextState = !_isStrobeActive.value
        cancelFlashingPatterns()
        if (nextState) {
            _isStrobeActive.value = true
            startStrobeLoop()
        }
    }

    @Synchronized
    fun toggleSos() {
        val nextState = !_isSosActive.value
        cancelFlashingPatterns()
        if (nextState) {
            _isSosActive.value = true
            startSosLoop()
        }
    }

    private fun cancelFlashingPatterns() {
        activeJob?.cancel()
        activeJob = null
        _isStrobeActive.value = false
        _isSosActive.value = false
    }

    private fun startStrobeLoop() {
        activeJob = controllerScope.launch {
            try {
                while (isActive) {
                    setTorchRaw(true)
                    delay(120)
                    setTorchRaw(false)
                    delay(120)
                }
            } catch (e: CancellationException) {
                setTorchRaw(false)
            }
        }
    }

    private fun startSosLoop() {
        activeJob = controllerScope.launch {
            try {
                // S.O.S. pattern:
                // S: . . . (short: 200ms on, 200ms off)
                // O: - - - (long: 600ms on, 200ms off)
                // S: . . . (short: 200ms on, 200ms off)
                // Gap between letters: 600ms
                // Gap between loops: 1500ms
                while (isActive) {
                    // S
                    repeat(3) {
                        setTorchRaw(true)
                        delay(200)
                        setTorchRaw(false)
                        delay(200)
                    }
                    delay(400) // extra space to letter

                    // O
                    repeat(3) {
                        setTorchRaw(true)
                        delay(600)
                        setTorchRaw(false)
                        delay(200)
                    }
                    delay(400) // extra space to letter

                    // S
                    repeat(3) {
                        setTorchRaw(true)
                        delay(200)
                        setTorchRaw(false)
                        delay(200)
                    }
                    delay(1500) // delay before starting again
                }
            } catch (e: CancellationException) {
                setTorchRaw(false)
            }
        }
    }

    fun cleanup() {
        cancelFlashingPatterns()
        if (isCallbackRegistered) {
            try {
                cameraManager?.unregisterTorchCallback(torchCallback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isCallbackRegistered = false
        }
    }
}
