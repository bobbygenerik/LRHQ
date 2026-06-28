package com.livingroomhq.core.data.repo

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.livingroomhq.core.data.model.SystemStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.RandomAccessFile

/**
 * Polls device metrics for the Command Center dashboard. Emits a fresh
 * [SystemStats] every [intervalMillis] without waking anything expensive:
 * /proc for CPU, ActivityManager for RAM, StatFs for storage and
 * TrafficStats deltas for network throughput.
 */
class SystemMonitor(
    private val context: Context,
    private val intervalMillis: Long = 2_000L,
) {

    fun stats(): Flow<SystemStats> = flow {
        var lastCpu = readCpuTicks()
        var lastRx = TrafficStats.getTotalRxBytes()
        var lastTx = TrafficStats.getTotalTxBytes()
        var lastAt = SystemClock.elapsedRealtime()

        while (true) {
            delay(intervalMillis)

            val cpu = readCpuTicks()
            val cpuPercent = cpuPercent(lastCpu, cpu)
            lastCpu = cpu

            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val now = SystemClock.elapsedRealtime()
            val seconds = ((now - lastAt) / 1000f).coerceAtLeast(0.001f)
            val downKbps = ((rx - lastRx) / 1024f / seconds).toLong().coerceAtLeast(0)
            val upKbps = ((tx - lastTx) / 1024f / seconds).toLong().coerceAtLeast(0)
            lastRx = rx; lastTx = tx; lastAt = now

            emit(
                SystemStats(
                    cpuPercent = cpuPercent,
                    ramUsedMb = ramUsedMb(),
                    ramTotalMb = ramTotalMb(),
                    storageUsedBytes = storageUsed(),
                    storageTotalBytes = storageTotal(),
                    networkDownKbps = downKbps,
                    networkUpKbps = upKbps,
                    vpnActive = isVpnActive(),
                    uptimeMillis = SystemClock.elapsedRealtime(),
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    /** (idle, total) jiffies from /proc/stat; unreadable on some SELinux policies, falls back to 0s. */
    private fun readCpuTicks(): Pair<Long, Long> = runCatching {
        RandomAccessFile("/proc/stat", "r").use { file ->
            val parts = file.readLine().split(Regex("\\s+")).drop(1).map { it.toLong() }
            val idle = parts[3] + parts.getOrElse(4) { 0L }
            idle to parts.sum()
        }
    }.getOrDefault(0L to 0L)

    private fun cpuPercent(prev: Pair<Long, Long>, cur: Pair<Long, Long>): Float {
        val totalDelta = cur.second - prev.second
        if (totalDelta <= 0) return 0f
        val idleDelta = cur.first - prev.first
        return ((totalDelta - idleDelta).toFloat() / totalDelta * 100f).coerceIn(0f, 100f)
    }

    private fun memoryInfo(): ActivityManager.MemoryInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also(am::getMemoryInfo)
    }

    private fun ramTotalMb() = memoryInfo().totalMem / (1024 * 1024)
    private fun ramUsedMb() = memoryInfo().let { (it.totalMem - it.availMem) / (1024 * 1024) }

    private fun dataStat() = StatFs(Environment.getDataDirectory().path)
    private fun storageTotal() = dataStat().let { it.blockCountLong * it.blockSizeLong }
    private fun storageUsed() = dataStat().let { (it.blockCountLong - it.availableBlocksLong) * it.blockSizeLong }

    private fun isVpnActive(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}
