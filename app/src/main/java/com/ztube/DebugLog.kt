package com.ztube

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object DebugLog {

    private const val CAPACITY = 400
    private const val FILE_NAME = "debug_log.txt"
    private const val PREVIOUS_FILE_NAME = "debug_log_previous.txt"
    private const val MAX_LINE = 300
    private const val SAMPLE_INTERVAL_MS = 5_000L
    private const val PERSIST_EVERY = 6

    private val lines = arrayOfNulls<String>(CAPACITY)
    private var next = 0
    private var size = 0
    private val startedAt = SystemClock.elapsedRealtime()

    val fetches = AtomicInteger()
    val droppedFrames = AtomicLong()

    private var file: File? = null
    private var handler: Handler? = null
    private var ticks = 0
    private var lastFetches = 0
    private var lastDropped = 0L

    fun log(tag: String, msg: String) {
        val builder = StringBuilder(64 + msg.length)
        appendTime(builder, SystemClock.elapsedRealtime() - startedAt)
        builder.append(' ').append(Thread.currentThread().name).append(' ').append(tag).append(": ").append(msg)
        val line = if (builder.length > MAX_LINE) builder.substring(0, MAX_LINE) else builder.toString()
        synchronized(lines) {
            lines[next] = line
            next = (next + 1) % CAPACITY
            if (size < CAPACITY) size++
        }
    }

    fun snapshot(): String {
        val builder = StringBuilder()
        synchronized(lines) {
            val first = (next - size + CAPACITY) % CAPACITY
            for (i in 0 until size) builder.append(lines[(first + i) % CAPACITY]).append('\n')
        }
        return builder.toString()
    }

    fun memory(): String {
        val runtime = Runtime.getRuntime()
        val used = (runtime.totalMemory() - runtime.freeMemory()) shr 20
        val max = runtime.maxMemory() shr 20
        val native = Debug.getNativeHeapAllocatedSize() shr 20
        return "used=${used}MB max=${max}MB native=${native}MB"
    }

    fun start(context: Context) {
        if (handler != null) return
        val dir = context.applicationContext.filesDir
        val current = File(dir, FILE_NAME)
        file = current
        val thread = HandlerThread("debug-sampler")
        thread.isDaemon = true
        thread.start()
        val sampler = Handler(thread.looper)
        handler = sampler
        sampler.post {
            try {
                if (current.exists()) current.renameTo(File(dir, PREVIOUS_FILE_NAME))
            } catch (_: Throwable) {
            }
        }
        sampler.post(object : Runnable {
            override fun run() {
                try {
                    sample()
                } catch (_: Throwable) {
                } finally {
                    sampler.postDelayed(this, SAMPLE_INTERVAL_MS)
                }
            }
        })
    }

    fun previous(context: Context): String? {
        val previous = File(context.applicationContext.filesDir, PREVIOUS_FILE_NAME)
        return try {
            if (previous.exists()) previous.readText() else null
        } catch (_: Throwable) {
            null
        }
    }

    fun persist() {
        handler?.post { write() }
    }

    private fun sample() {
        val fetched = fetches.get()
        val dropped = droppedFrames.get()
        val counters = if (fetched != lastFetches || dropped != lastDropped) " fetches=$fetched dropped=$dropped" else ""
        lastFetches = fetched
        lastDropped = dropped
        log("Mem", memory() + counters)
        if (++ticks % PERSIST_EVERY == 0) write()
    }

    private fun write() {
        try {
            file?.writeText(snapshot())
        } catch (_: Throwable) {
        }
    }

    private fun appendTime(builder: StringBuilder, elapsed: Long) {
        val totalSeconds = elapsed / 1000
        appendPadded(builder, totalSeconds / 3600, 2)
        builder.append(':')
        appendPadded(builder, totalSeconds / 60 % 60, 2)
        builder.append(':')
        appendPadded(builder, totalSeconds % 60, 2)
        builder.append('.')
        appendPadded(builder, elapsed % 1000, 3)
    }

    private fun appendPadded(builder: StringBuilder, value: Long, width: Int) {
        val digits = value.toString()
        for (i in digits.length until width) builder.append('0')
        builder.append(digits)
    }
}
