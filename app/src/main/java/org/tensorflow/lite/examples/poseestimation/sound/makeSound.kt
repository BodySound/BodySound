package org.tensorflow.lite.examples.poseestimation.sound

import android.graphics.PointF
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioTrack.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.*
import java.util.*
import kotlin.math.sin

class MakeSound() {

    private val sampleRate = 22050//44100 //샘플링 정도 혹시 너무 버벅거리면 샘플링 줄이기
    private var minSize = getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private var is_record: Boolean = false
    private var File_Path: String = ""
//    private var file = File("./" + File_Path)
    private var file: File? = null
    private var record_CD = mutableListOf<ShortArray>()

    private var ratio: Float = 0.0F
    private var Right_Wrist: PointF = PointF(0.0F, 0.0F)
    var playState = false //재생중:true, 정지:false
    var recordPlayState = false
    private var angle: Double = 0.0
    private var recordAngle: Double = 0.0
    private var audioTrack: AudioTrack? = null
    private var recordAudioTrack: AudioTrack?=null
    private var startFrequency = 130.81 // 초기 주파수 값 ==> 시작점
    private var synthFrequency = 130.81 // 시작점으로부터 시작하는 주파수 변화
    private var buffer = ShortArray(minSize)// 버퍼
    private var player = getAudioTrack() // 소리 재생 클라스 생성
    private var recordPlayer = getRecordTrack()
    var soundThread: Thread? = null //스레드
    var recordPlayThread: Thread? = null
    var recordThreads = mutableListOf<Runnable>()
    /*************************************************************** sound thread *******************************/
    @RequiresApi(Build.VERSION_CODES.M)
    var soundGen = Runnable { //버퍼 생성 스레드
        Thread.currentThread().priority = Thread.MIN_PRIORITY
        if (Thread.currentThread().isInterrupted) {
            return@Runnable
        }
        else {
            player?.play()
            while(playState) {
                generateTone()
                if (is_record) {
                    Log.d("test", "is recording")
                    this.record_CD.add(buffer)
                }
                player?.write(buffer, 0, buffer.size, WRITE_BLOCKING)
            }
            player?.stop()
        }
    }
    var playRecorded = Runnable { //버퍼 생성 스레드
        if (Thread.currentThread().isInterrupted) {
            return@Runnable
        }
        else {
            while(true) {
                if (recordPlayState == true) {
                    var recordBuffer = playRecord()
                    recordPlayer?.play()
                    //Log.d("test", "start recordplay")
                    for (buf in recordBuffer) {
                         recordPlayer?.write(buf, 0, buf.size, WRITE_BLOCKING)
                    }
                    return@Runnable
                }
                else {
                    recordPlayer?.stop()
                    return@Runnable
                }

            }
        }
    }
    /************************************************ start stop sound functions ***********************/
    @RequiresApi(Build.VERSION_CODES.M)
    private fun makeSound() { //소리 재생
        playState = true
        soundThread = Thread(soundGen)
        soundThread!!.start()
    }

    private fun stopSound() { //소리 멈춤
        playState = false
    }
    fun closeAudioTrack(){
        player?.stop()
    }
    /************************************************* get functions ************************************/
    private fun getStartNoteFrequencies(): Double { //주파수 리턴 함수
        return startFrequency
    }

    private fun getSynthNoteFrequencies(): Double {
        return synthFrequency
    }

    private fun getRatio(): Float {
        return ratio
    }

    private fun getRightWrist(): PointF {
        return Right_Wrist
    }

    /************************************************* set functions ************************************/
    private fun setStartFrequencies(note: Double) {//ex) input(note) = (Note.C4.note) ==>파라미터
        startFrequency = note
    }

    private fun setNoteFrequencies(ratio: Double) { //주파수 조절 함수 : 아직 확정 못 지음
        synthFrequency = startFrequency + (ratio * (startFrequency) * 0.5)
    }
    private fun setNoteFrequenciesZero() { //주파수 조절
        synthFrequency = 0.0
    }
    private fun setRatio(Ratio: Float) {
        ratio = Ratio
    }

    private fun setRightWrist(RightWrist: PointF) {
        Right_Wrist = RightWrist
    }

    /************************************************* controlling sound options *************************/
    private fun oscillator(
        amplify: Double,
        frequencies: Double
    ): Double { //파형 조절 함수 amplify:진폭 frequencies:주파수
        return sin( Math.PI * frequencies)
    }

    private fun generateTone() {// 버퍼 생성 함수 array에 집어넣을 값
        for (i in buffer.indices) {
            val angularFrequency: Double =
                synthFrequency * (Math.PI) / sampleRate
            buffer[i] = (Short.MAX_VALUE * oscillator(1.5, angle).toFloat()).toInt().toShort()
            angle += angularFrequency
        }
    }
    /************************************************ initializing audiotrack *****************************/
    private fun getAudioTrack(): AudioTrack? {// 오디오 트랙 빌더 => 오디오 트랙 생성
        if (audioTrack == null) audioTrack = Builder().setTransferMode(MODE_STREAM)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minSize)
            .build()
        return audioTrack
    }
    private fun getRecordTrack(): AudioTrack? {// 오디오 트랙 빌더 => 오디오 트랙 생성
        if (recordAudioTrack == null) recordAudioTrack = Builder().setTransferMode(MODE_STREAM)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minSize)
            .build()
        return recordAudioTrack
    }

    private fun createRunnable() :Runnable{
        var initRunnable = Runnable {
            if (Thread.currentThread().isInterrupted) {
                return@Runnable
            } else {
                var recordBuffer = mutableListOf<ShortArray>()
                recordBuffer = playRecord()
                recordPlayer?.play()
                while (recordPlayState) {
                    if (recordPlayState == true) {
                        for (buf in recordBuffer) {
                            Log.d("test", "start recordplay")
                            recordPlayer?.write(buf, 0, buf.size, WRITE_BLOCKING)
                        }
                    } else {
                        recordPlayer?.stop()
                        return@Runnable
                    }
                }
            }
        }
        return initRunnable
    }
    fun soundPlay(ratio: Float, right_wrist: PointF) {
        this.ratio = 0.0f
        //this.Right_Wrist = right_wrist
        //this.is_in_body = is_in_body
        var distance = Math.pow(
            (right_wrist.x - Right_Wrist.x).toDouble(),
            2.0
        ) + Math.pow((right_wrist.y - Right_Wrist.y).toDouble(), 2.0)
        if (distance > 100) {
            setNoteFrequencies(ratio.toDouble())
            /*var I_ratio = (ratio * 50.0f).toInt()
            if (I_ratio == PI_ratio)
                return
            PI_ratio = I_ratio*/
        } else if (distance <= 100 && playState) {
            setNoteFrequenciesZero()
            //makeSound()
            //stopSound()
        }
        if (!playState) makeSound()
        Right_Wrist = right_wrist
    }
    fun startRecord(Filepath: File?) {
        //Filepath 중복체크 해야됨
//        this.File_Path = Filepath
        this.file = Filepath
        this.is_record = true
        Log.d("test", "start record")
        Log.d("test", Filepath.toString())
        File_Path = Filepath.toString()
    }
    fun stopRecord(file_name: String) {
        this.is_record = false

        Log.d("test", file_name)
        val fos = FileOutputStream("$File_Path/$file_name.bin")
        val oos = ObjectOutputStream(fos)
        for(buf in this.record_CD) {
            oos.writeObject(buf)
        }
        oos.close()

        this.record_CD.clear()
    }
    fun playRecord(/*Filepath: String*/): MutableList<ShortArray> {
        /*var recordPlayer = getAudioTrack()
        recordPlayer?.play()
        recordPlayThread = Thread(playRecorded)*/
        val fis = FileInputStream(File_Path + "/test5.bin")
        val ois = ObjectInputStream(fis)
        var play_CD = mutableListOf<ShortArray>()
        var buff :ShortArray

        while(true) {
            buff = (ois.readObject() as ShortArray)
            if(buff == null){
                ois.close()
                return play_CD
            }
            else{
                play_CD.add(buff)
            }
            Log.d("test5", play_CD.toString())
        }
    }
}
