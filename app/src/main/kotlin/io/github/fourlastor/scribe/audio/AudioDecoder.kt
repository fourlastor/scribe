package io.github.fourlastor.scribe.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** The file holds no audio the device can decode. */
class UnsupportedAudioException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Decodes audio files to the mono 16 kHz float samples speech models expect. */
class AudioDecoder {

    /**
     * Decodes [file] from start to end, handing the samples to [onSamples] in blocks of about
     * half a second. [onDuration] is called first if the container states how long the audio is.
     *
     * Decoding only moves on once [onSamples] returns, so a slow consumer holds it back rather
     * than piling audio up in memory.
     */
    suspend fun decode(
        file: File,
        onDuration: suspend (durationMs: Long) -> Unit,
        onSamples: suspend (FloatArray) -> Unit,
    ) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            val format = try {
                extractor.setDataSource(file.path)
                selectAudioTrack(extractor)
            } catch (e: IOException) {
                throw UnsupportedAudioException("Not a readable media file", e)
            } ?: throw UnsupportedAudioException("No audio track")

            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                onDuration(format.getLong(MediaFormat.KEY_DURATION) / 1000)
            }

            // Ask for 16-bit output explicitly, some decoders would otherwise pick float.
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = try {
                MediaCodec.createDecoderByType(mime).apply {
                    configure(format, null, null, 0)
                    start()
                }
            } catch (e: Exception) {
                throw UnsupportedAudioException("No decoder for $mime", e)
            }
            pump(extractor, codec, format, onSamples)
        } finally {
            codec?.runCatching { stop() }
            codec?.release()
            extractor.release()
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): MediaFormat? {
        for (track in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(track)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                extractor.selectTrack(track)
                return format
            }
        }
        return null
    }

    private suspend fun pump(
        extractor: MediaExtractor,
        codec: MediaCodec,
        inputFormat: MediaFormat,
        onSamples: suspend (FloatArray) -> Unit,
    ) {
        var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        var resampler = Resampler(inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE), SAMPLE_RATE)
        val block = SampleBlock(BLOCK_SAMPLES)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            coroutineContext.ensureActive()

            if (!inputDone) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // The decoder knows the real layout only once it has seen the stream. Opus, for
                // one, always decodes to 48 kHz whatever the container header says.
                val format = codec.outputFormat
                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                encoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                resampler = Resampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), SAMPLE_RATE)
            } else if (index >= 0) {
                val mono = if (info.size > 0) {
                    val buffer = codec.getOutputBuffer(index)!!
                    buffer.position(info.offset).limit(info.offset + info.size)
                    downmix(buffer.order(ByteOrder.nativeOrder()), channels, encoding)
                } else {
                    null
                }
                codec.releaseOutputBuffer(index, false)
                if (mono != null) block.add(resampler.process(mono), onSamples)
                outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            }
        }

        block.add(resampler.flush(), onSamples)
        block.flush(onSamples)
    }

    private fun downmix(buffer: ByteBuffer, channels: Int, encoding: Int): FloatArray {
        // The request for 16-bit output is only a hint: float WAV files still decode to float.
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val pcm = buffer.asFloatBuffer()
            return FloatArray(pcm.remaining() / channels) {
                var sum = 0f
                repeat(channels) { sum += pcm.get() }
                sum / channels
            }
        }
        val pcm = buffer.asShortBuffer()
        return FloatArray(pcm.remaining() / channels) {
            var sum = 0
            repeat(channels) { sum += pcm.get() }
            sum / (channels * 32768f)
        }
    }

    /** Collects decoder output, which arrives a few milliseconds at a time, into larger blocks. */
    private class SampleBlock(private val capacity: Int) {
        private var samples = FloatArray(capacity)
        private var size = 0

        suspend fun add(input: FloatArray, emit: suspend (FloatArray) -> Unit) {
            var offset = 0
            while (offset < input.size) {
                val count = minOf(capacity - size, input.size - offset)
                input.copyInto(samples, size, offset, offset + count)
                size += count
                offset += count
                if (size == capacity) flush(emit)
            }
        }

        suspend fun flush(emit: suspend (FloatArray) -> Unit) {
            if (size == 0) return
            val full = samples.copyOf(size)
            size = 0
            emit(full)
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000

        private const val BLOCK_SAMPLES = SAMPLE_RATE / 2
        private const val TIMEOUT_US = 10_000L
    }
}
