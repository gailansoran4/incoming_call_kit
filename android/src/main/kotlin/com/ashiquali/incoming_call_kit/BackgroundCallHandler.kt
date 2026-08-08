package com.ashiquali.incoming_call_kit

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation

object BackgroundCallHandler {
    private const val TAG = "BackgroundCallHandler"
    private const val BACKGROUND_CHANNEL = "com.ashiquali.incoming_call_kit/background"
    private const val DESTROY_GRACE_MS = 1500L

    private var flutterEngine: FlutterEngine? = null
    private val pendingEventQueue = mutableListOf<Map<String, Any?>>()
    private var isEngineReady = false
    private var inflightEventCount = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var destroyRunnable: Runnable? = null

    fun setCallbackHandles(
        context: Context,
        dispatcherHandle: Long,
        userCallbackHandle: Long,
    ) {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(Constants.PREFS_BACKGROUND_CALLBACK_HANDLE, dispatcherHandle)
            .putLong(Constants.PREFS_USER_CALLBACK_HANDLE, userCallbackHandle)
            .commit()
    }

    fun getDispatcherHandle(context: Context): Long {
        return context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(Constants.PREFS_BACKGROUND_CALLBACK_HANDLE, 0)
    }

    fun getUserCallbackHandle(context: Context): Long {
        return context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(Constants.PREFS_USER_CALLBACK_HANDLE, 0)
    }

    fun hasHandler(context: Context): Boolean {
        return getDispatcherHandle(context) != 0L && getUserCallbackHandle(context) != 0L
    }

    @Synchronized
    fun dispatchEvent(context: Context, event: Map<String, Any?>) {
        val dispatcherHandle = getDispatcherHandle(context)
        val userCallbackHandle = getUserCallbackHandle(context)
        if (dispatcherHandle == 0L || userCallbackHandle == 0L) {
            Log.w(TAG, "No background handler registered, persisting event")
            CallKitConfigStore.storePendingEvent(context, event)
            return
        }

        cancelScheduledDestroy()
        pendingEventQueue.add(event)

        if (flutterEngine != null && isEngineReady) {
            flushEvents(context)
            return
        }

        if (flutterEngine != null) {
            // Engine starting but not ready yet — event is queued, will flush on ready
            return
        }

        try {
            ensureFlutterInitialized(context)

            val callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(dispatcherHandle)
            if (callbackInfo == null) {
                Log.e(TAG, "Failed to lookup dispatcher callback for handle: $dispatcherHandle")
                persistQueuedEvents(context)
                return
            }

            flutterEngine = FlutterEngine(context.applicationContext, null, false)
            val engine = flutterEngine!!
            isEngineReady = false

            val backgroundChannel = MethodChannel(
                engine.dartExecutor.binaryMessenger,
                BACKGROUND_CHANNEL
            )

            backgroundChannel.setMethodCallHandler { call: MethodCall, result: MethodChannel.Result ->
                if (call.method == "backgroundHandlerInitialized") {
                    isEngineReady = true
                    flushEvents(context)
                    result.success(null)
                } else {
                    result.notImplemented()
                }
            }

            val appBundlePath = FlutterInjector.instance().flutterLoader().findAppBundlePath()
            engine.dartExecutor.executeDartCallback(
                DartExecutor.DartCallback(
                    context.assets,
                    appBundlePath,
                    callbackInfo
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch background event", e)
            persistQueuedEvents(context)
            destroyEngine()
        }
    }

    private fun ensureFlutterInitialized(context: Context) {
        val loader = FlutterInjector.instance().flutterLoader()
        if (!loader.initialized()) {
            loader.startInitialization(context.applicationContext)
            loader.ensureInitializationComplete(context.applicationContext, null)
        }
    }

    @Synchronized
    private fun flushEvents(context: Context) {
        val engine = flutterEngine ?: return
        if (!isEngineReady) return

        val backgroundChannel = MethodChannel(
            engine.dartExecutor.binaryMessenger,
            BACKGROUND_CHANNEL
        )
        val toFlush = pendingEventQueue.toList()
        pendingEventQueue.clear()
        if (toFlush.isEmpty()) {
            scheduleDestroy()
            return
        }

        val userCallbackHandle = getUserCallbackHandle(context)
        inflightEventCount += toFlush.size

        for (event in toFlush) {
            val payload = event.toMutableMap()
            payload["userCallbackHandle"] = userCallbackHandle
            backgroundChannel.invokeMethod(
                "onBackgroundEvent",
                payload,
                object : MethodChannel.Result {
                    override fun success(result: Any?) {
                        onBackgroundEventFinished()
                    }

                    override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                        Log.e(TAG, "Background event failed: $errorCode $errorMessage")
                        onBackgroundEventFinished()
                    }

                    override fun notImplemented() {
                        Log.e(TAG, "Background event not implemented")
                        onBackgroundEventFinished()
                    }
                },
            )
        }
    }

    @Synchronized
    private fun onBackgroundEventFinished() {
        inflightEventCount = (inflightEventCount - 1).coerceAtLeast(0)
        if (inflightEventCount == 0 && pendingEventQueue.isEmpty()) {
            scheduleDestroy()
        }
    }

    private fun scheduleDestroy() {
        cancelScheduledDestroy()
        val runnable = Runnable { destroyEngine() }
        destroyRunnable = runnable
        mainHandler.postDelayed(runnable, DESTROY_GRACE_MS)
    }

    private fun cancelScheduledDestroy() {
        destroyRunnable?.let { mainHandler.removeCallbacks(it) }
        destroyRunnable = null
    }

    private fun persistQueuedEvents(context: Context) {
        for (event in pendingEventQueue) {
            CallKitConfigStore.storePendingEvent(context, event)
        }
        pendingEventQueue.clear()
    }

    @Synchronized
    fun destroyEngine() {
        cancelScheduledDestroy()
        isEngineReady = false
        inflightEventCount = 0
        flutterEngine?.destroy()
        flutterEngine = null
    }
}
