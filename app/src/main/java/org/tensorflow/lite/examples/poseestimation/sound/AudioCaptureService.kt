package org.tensorflow.lite.examples.poseestimation.sound

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.experimental.and
import android.os.Build
import androidx.annotation.RequiresApi

class AudioCaptureService : Service() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null

    private lateinit var audioCaptureThread: Thread
    private var audioRecord: AudioRecord? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        Log.d("stamp" ,"onCreate")
        super.onCreate()
        createNotificationChannel()
        startForeground(
            SERVICE_ID,
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).build()
        )
        // use applicationContext to avoid memory leak on Android 10.
        // see: https://partnerissuetracker.corp.google.com/issues/139732252
        mediaProjectionManager =
            applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        Log.d("stamp" ,"createNotificationChannel")
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Audio Capture Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java) as NotificationManager
        manager.createNotificationChannel(serviceChannel)
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("stamp" ,"onStartCommand")
        return if (intent != null) {
            when (intent.action) {
                ACTION_START -> {
                    mediaProjection =
                        mediaProjectionManager.getMediaProjection(
                            Activity.RESULT_OK,
                            intent.getParcelableExtra(EXTRA_RESULT_DATA)!!
                        ) as MediaProjection
                    startAudioCapture()
                    Service.START_STICKY
                }
                ACTION_STOP -> {
                    stopAudioCapture()
                    Service.START_NOT_STICKY
                }
                else -> throw IllegalArgumentException("Unexpected action received: ${intent.action}")
            }
        } else {
            Service.START_NOT_STICKY
        }
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startAudioCapture() {
        Log.d("stamp" ,"startAudioCapture")
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA) // TODO provide UI options for inclusion/exclusion
            .build()
        /**
         * Using hardcoded values for the audio format, Mono PCM samples with a sample rate of 8000Hz
         * These can be changed according to your application's needs
         */

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(88200)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            // For optimal performance, the buffer size
            // can be optionally specified to store audio samples.
            // If the value is not specified,
            // uses a single frame and lets the
            // native code figure out the minimum buffer size.
            .setBufferSizeInBytes(BUFFER_SIZE_IN_BYTES)
            .setAudioPlaybackCaptureConfig(config)
            .build()
        audioRecord!!.startRecording()

        audioCaptureThread = thread(start = true) {
            val outputFile = createAudioFile()
            Log.d(LOG_TAG, "Created file for capture target: ${outputFile.absolutePath}")
            writeAudioToFile(outputFile)
        }
    }
    private fun createAudioFile(): File {
        Log.d("stamp" ,"createAudioFile")
        val audioCapturesDirectory = File(getExternalFilesDir(null), "/AudioCaptures")
        if (!audioCapturesDirectory.exists()) {
            audioCapturesDirectory.mkdirs()
        }
        val fileName = "Capture-BodySound.pcm"
        return File(audioCapturesDirectory.absolutePath + "/" + fileName)
    }
    private fun writeAudioToFile(outputFile: File) {

        Log.d("stamp" ,"writeAudioToFile")
        val fileOutputStream = FileOutputStream(outputFile)
        val capturedAudioSamples = ShortArray(NUM_SAMPLES_PER_READ)
        while (!audioCaptureThread.isInterrupted) {
            audioRecord?.read(capturedAudioSamples, 0, NUM_SAMPLES_PER_READ)
            // This loop should be as fast as possible to avoid artifacts in the captured audio
            // You can uncomment the following line to see the capture samples but
            // that will incur a performance hit due to logging I/O.
            // Log.v(LOG_TAG, "Audio samples captured: ${capturedAudioSamples.toList()}")
            fileOutputStream.write(
                capturedAudioSamples.toByteArray(),
                0,
                BUFFER_SIZE_IN_BYTES
            )
        }
        fileOutputStream.close()
        Log.d(LOG_TAG, "Audio capture finished for ${outputFile.absolutePath}. File size is ${outputFile.length()} bytes.")
    }
    private fun stopAudioCapture() {
        Log.d("stamp" ,"stopAudioCapture")
        requireNotNull(mediaProjection) { "Tried to stop audio capture, but there was no ongoing capture in place!" }
        audioCaptureThread.interrupt()
        audioCaptureThread.join()
        audioRecord!!.stop()
        audioRecord!!.release()
        audioRecord = null
        mediaProjection!!.stop()
        stopSelf()
    }
    override fun onBind(p0: Intent?): IBinder? = null
    private fun ShortArray.toByteArray(): ByteArray {
        Log.d("stamp" ,"toByteArray")
        // Samples get translated into bytes following little-endianness:
        // least significant byte first and the most significant byte last
        val bytes = ByteArray(size * 2)
        for (i in 0 until size) {
            bytes[i * 2] = (this[i] and 0x00FF).toByte()
            bytes[i * 2 + 1] = (this[i].toInt() shr 8).toByte()
            this[i] = 0
        }
        return bytes
    }
    companion object {
        private const val LOG_TAG = "AudioCaptureService"
        private const val SERVICE_ID = 123
        private const val NOTIFICATION_CHANNEL_ID = "AudioCapture channel"

        private const val NUM_SAMPLES_PER_READ = 1024
        private const val BYTES_PER_SAMPLE = 2 // 2 bytes since we hardcoded the PCM 16-bit format
        private const val BUFFER_SIZE_IN_BYTES = NUM_SAMPLES_PER_READ * BYTES_PER_SAMPLE

        const val ACTION_START = "AudioCaptureService:Start"
        const val ACTION_STOP = "AudioCaptureService:Stop"
        const val EXTRA_RESULT_DATA = "AudioCaptureService:Extra:ResultData"
    }
}