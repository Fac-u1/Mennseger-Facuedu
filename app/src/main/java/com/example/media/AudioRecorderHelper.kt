package com.example.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

class AudioRecorderHelper(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): File {
        stopAndRelease()
        val file = File(context.cacheDir, "voice_${UUID.randomUUID()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(96000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        outputFile = file
        return file
    }

    fun stopRecording(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            outputFile
        } catch (_: Exception) {
            stopAndRelease()
            null
        }
    }

    fun stopAndRelease() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
    }
}
