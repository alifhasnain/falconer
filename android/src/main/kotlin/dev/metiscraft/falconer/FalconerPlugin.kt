package dev.metiscraft.falconer

import dev.metiscraft.falconer.channel.MethodNames
import dev.metiscraft.falconer.channel.PayloadMapper
import dev.metiscraft.falconer.config.FalconerNativeConfig
import dev.metiscraft.falconer.data.FalconerDatabase
import dev.metiscraft.falconer.data.PrefsLastCleanupStore
import android.content.Context
import dev.metiscraft.falconer.data.RetentionManager
import dev.metiscraft.falconer.data.TransactionRepository
import dev.metiscraft.falconer.notification.NotificationHelper
import dev.metiscraft.falconer.notification.NotificationPermission
import dev.metiscraft.falconer.ui.FalconerActivity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * FalconerPlugin — wires the `falconer` MethodChannel and the
 * `falconer/transactionCount` EventChannel to the Room-backed repository.
 *
 * Log calls are handled fire-and-forget on an IO scope (Dart already does not
 * await them); each replies `success(null)` immediately. Implements
 * [ActivityAware] for the Phase 6 notification-permission flow.
 */
class FalconerPlugin :
    FlutterPlugin,
    ActivityAware,
    MethodCallHandler,
    EventChannel.StreamHandler {

    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var repository: TransactionRepository? = null
    private var retention: RetentionManager? = null
    private var countJob: Job? = null
    private var notifJob: Job? = null

    @Volatile
    private var nativeConfig: FalconerNativeConfig = FalconerNativeConfig.DEFAULT

    private var appContext: Context? = null
    private var notificationHelper: NotificationHelper? = null
    private val notificationPermission = NotificationPermission()

    private var activityBinding: ActivityPluginBinding? = null

    // FlutterPlugin -----------------------------------------------------------

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        val context = binding.applicationContext
        appContext = context
        val dao = FalconerDatabase.get(context).transactionDao()
        val retentionManager = RetentionManager(
            lastCleanup = PrefsLastCleanupStore(context),
            deleteBefore = dao::deleteBefore,
        )
        retention = retentionManager
        repository = TransactionRepository(dao, retentionManager) { nativeConfig }

        // Startup retention pass.
        scope.launch { repository?.cleanupOnStart() }

        // Throttled ongoing notification reflecting live capture.
        notificationHelper = NotificationHelper(context)
        notifJob = scope.launch {
            repository?.observeAll()?.conflate()?.collect { list ->
                if (nativeConfig.showNotification && list.isNotEmpty()) {
                    notificationHelper?.show(list, list.size)
                } else {
                    notificationHelper?.dismiss()
                }
                delay(NOTIFICATION_THROTTLE_MS)
            }
        }

        methodChannel = MethodChannel(binding.binaryMessenger, MethodNames.CHANNEL)
        methodChannel.setMethodCallHandler(this)

        eventChannel = EventChannel(binding.binaryMessenger, MethodNames.TRANSACTION_COUNT_CHANNEL)
        eventChannel.setStreamHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        countJob?.cancel()
        notifJob?.cancel()
        notificationHelper?.dismiss()
        scope.cancel()
    }

    // MethodCallHandler -------------------------------------------------------

    override fun onMethodCall(
        call: MethodCall,
        result: Result
    ) {
        when (call.method) {
            MethodNames.PING -> result.success("pong")

            MethodNames.CONFIGURE -> {
                (call.arguments as? Map<*, *>)?.let { map ->
                    val config = FalconerNativeConfig.fromMap(map)
                    nativeConfig = config
                    retention?.period = config.retention
                }
                scope.launch { repository?.cleanupOnStart() }
                result.success(null)
            }

            MethodNames.LOG_REQUEST -> {
                (call.arguments as? Map<*, *>)?.let { map ->
                    scope.launch { repository?.logRequest(PayloadMapper.parseRequest(map)) }
                }
                result.success(null)
            }

            MethodNames.LOG_RESPONSE -> {
                (call.arguments as? Map<*, *>)?.let { map ->
                    scope.launch { repository?.logResponse(PayloadMapper.parseResponse(map)) }
                }
                result.success(null)
            }

            MethodNames.LOG_ERROR -> {
                (call.arguments as? Map<*, *>)?.let { map ->
                    scope.launch { repository?.logError(PayloadMapper.parseError(map)) }
                }
                result.success(null)
            }

            MethodNames.CLEAR_TRANSACTIONS -> {
                scope.launch { repository?.clear() }
                result.success(null)
            }

            MethodNames.LAUNCH_UI -> {
                appContext?.let { FalconerActivity.start(it) }
                result.success(null)
            }

            MethodNames.REQUEST_NOTIFICATION_PERMISSION -> {
                notificationPermission.request(activityBinding) { granted ->
                    result.success(granted)
                }
            }

            else -> result.notImplemented()
        }
    }

    // EventChannel.StreamHandler ---------------------------------------------

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        val repo = repository ?: return
        countJob?.cancel()
        // Collect on Main so the sink is touched on the platform thread.
        countJob = scope.launch(Dispatchers.Main) {
            repo.observeCount().collectLatest { count -> events?.success(count) }
        }
    }

    override fun onCancel(arguments: Any?) {
        countJob?.cancel()
        countJob = null
    }

    // ActivityAware (binding captured for the Phase 6 permission flow) --------

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        binding.addRequestPermissionsResultListener(notificationPermission)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activityBinding = binding
        binding.addRequestPermissionsResultListener(notificationPermission)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityBinding?.removeRequestPermissionsResultListener(notificationPermission)
        activityBinding = null
    }

    override fun onDetachedFromActivity() {
        activityBinding?.removeRequestPermissionsResultListener(notificationPermission)
        activityBinding = null
    }

    private companion object {
        const val NOTIFICATION_THROTTLE_MS = 700L
    }
}
