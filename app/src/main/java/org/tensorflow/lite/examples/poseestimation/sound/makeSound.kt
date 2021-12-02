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
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.examples.poseestimation.data.Note
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
    private var playState = false //재생중:true, 정지:false
    private var recordPlayState = false
    private var angle: Double = 0.0
    private var recordAngle: Double = 0.0
    private var audioTrack: AudioTrack? = null
    private var recordAudioTrack: AudioTrack?=null
    private var startFrequency = Note.C3.note // 초기 주파수 값 ==> 시작점
    private var synthFrequency = Note.C3.note // 시작점으로부터 시작하는 주파수 변화
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
                if (recordPlayState) {
                    var recordBuffer = playRecord()
                    recordPlayer?.play()
                    //Log.d("test", "start recordplay")
                    for (buf in recordBuffer) {
                         recordPlayer?.write(buf, 0, buf.size, WRITE_BLOCKING)
                    }
                    return@Runnable
                }
                else if(!recordPlayState)

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

    fun stopSound() { //소리 멈춤
        playState = false
    }
    fun startSound(){
        playState = true
    }
    fun closeAudioTrack(){
        player?.stop()
    }
    fun stopRecordPlay(){
        recordPlayState = false
    }
    fun startRecordPlay(){
        recordPlayState = true
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
    fun setStartFrequencies(note: String) {//ex) input(note) = (Note.C4.note) ==>파라미터
        when(note){
            "C0" -> {
                startFrequency = Note.C0.note
            }            "Cs0","Df0" -> {
                startFrequency = Note.Cs0.note
            }            "D0" -> {
                startFrequency = Note.D0.note
            }            "Ds0","Ef0" -> {
                startFrequency = Note.Ds0.note
            }            "E0" -> {
                startFrequency = Note.E0.note
            }            "F0" -> {
                startFrequency = Note.F0.note
            }            "Fs0","Gf0" -> {
                startFrequency = Note.Fs0.note
            }            "G0" -> {
                startFrequency = Note.G0.note
            }            "Gs0","Ab0" -> {
                startFrequency = Note.Gs0.note
            }            "A0" -> {
                startFrequency = Note.A0.note
            }            "As0","Bf0" -> {
                startFrequency = Note.As0.note
            }            "B0" -> {
                startFrequency = Note.B0.note
            }            "C1" -> {
                startFrequency = Note.C0.note
            }            "Cs1","Df1" -> {
            startFrequency = Note.Cs1.note
            }            "D1" -> {
            startFrequency = Note.D0.note
            }            "Ds1","Ef1" -> {
            startFrequency = Note.Ds1.note
            }            "E1" -> {
            startFrequency = Note.E0.note
            }            "F1" -> {
            startFrequency = Note.F0.note
            }            "Fs1","Gf1" -> {
            startFrequency = Note.Fs1.note
            }            "G1" -> {
            startFrequency = Note.G0.note
            }            "Gs1","Ab1" -> {
            startFrequency = Note.Gs1.note
            }            "A1" -> {
            startFrequency = Note.A0.note
            }            "As1","Bf1" -> {
            startFrequency = Note.As1.note
            }            "B1" -> {
            startFrequency = Note.B1.note
            }            "C2" -> {
                startFrequency = Note.C2.note
            }            "Cs2","Df2" -> {
            startFrequency = Note.Cs2.note
            }            "D2" -> {
            startFrequency = Note.D2.note
            }            "Ds2","Ef2" -> {
            startFrequency = Note.Ds2.note
            }            "E2" -> {
            startFrequency = Note.E2.note
            }            "F2" -> {
            startFrequency = Note.F2.note
            }            "Fs2","Gf2" -> {
            startFrequency = Note.Fs2.note
            }            "G2" -> {
            startFrequency = Note.G2.note
            }            "Gs2","Ab2" -> {
            startFrequency = Note.Gs2.note
            }            "A2" -> {
            startFrequency = Note.A2.note
            }            "As2","Bf2" -> {
            startFrequency = Note.As2.note
            }            "B2" -> {
            startFrequency = Note.B2.note
            }            "C3" -> {
                startFrequency = Note.C3.note
            }            "Cs3","Df3" -> {
            startFrequency = Note.Cs3.note
            }            "D3" -> {
            startFrequency = Note.D3.note
            }            "Ds3","Ef3" -> {
            startFrequency = Note.Ds3.note
            }            "E3" -> {
            startFrequency = Note.E3.note
            }            "F3" -> {
            startFrequency = Note.F3.note
            }            "Fs3","Gf3" -> {
            startFrequency = Note.Fs3.note
            }            "G3" -> {
            startFrequency = Note.G3.note
            }            "Gs3","Ab3" -> {
            startFrequency = Note.Gs3.note
            }            "A3" -> {
            startFrequency = Note.A3.note
            }            "As3","Bf3" -> {
            startFrequency = Note.As0.note
            }            "B3" -> {
            startFrequency = Note.B3.note
            }            "C4" -> {
                startFrequency = Note.C4.note
            }            "Cs4","Df4" -> {
            startFrequency = Note.Cs4.note
            }            "D4" -> {
            startFrequency = Note.D4.note
            }            "Ds4","Ef4" -> {
            startFrequency = Note.Ds4.note
            }            "E4" -> {
            startFrequency = Note.E4.note
            }            "F4" -> {
            startFrequency = Note.F4.note
            }            "Fs4","Gf4" -> {
            startFrequency = Note.Fs4.note
            }            "G0" -> {
            startFrequency = Note.G4.note
            }            "Gs4","Ab4" -> {
            startFrequency = Note.Gs4.note
            }            "A4" -> {
            startFrequency = Note.A0.note
            }            "As4","Bf4" -> {
            startFrequency = Note.As4.note
            }            "B4" -> {
            startFrequency = Note.B4.note
            }            "C5" -> {
                startFrequency = Note.C5.note
            }            "Cs5","Df5" -> {
            startFrequency = Note.Cs5.note
            }            "D5" -> {
            startFrequency = Note.D5.note
            }            "Ds5","Ef5" -> {
            startFrequency = Note.Ds5.note
            }            "E5" -> {
            startFrequency = Note.E5.note
            }            "F5" -> {
            startFrequency = Note.F0.note
            }            "Fs5","Gf5" -> {
            startFrequency = Note.Fs5.note
            }            "G5" -> {
            startFrequency = Note.G5.note
            }            "Gs5","Ab5" -> {
            startFrequency = Note.Gs5.note
            }            "A5" -> {
            startFrequency = Note.A5.note
            }            "As5","Bf5" -> {
            startFrequency = Note.As5.note
            }            "B5" -> {
            startFrequency = Note.B5.note
            }            "C6" -> {
                startFrequency = Note.C6.note
            }            "Cs6","Df6" -> {
            startFrequency = Note.Cs6.note
            }            "D6" -> {
            startFrequency = Note.D6.note
            }            "Ds6","Ef6" -> {
            startFrequency = Note.Ds6.note
            }            "E6" -> {
            startFrequency = Note.E6.note
            }            "F6" -> {
            startFrequency = Note.F6.note
            }            "Fs6","Gf6" -> {
            startFrequency = Note.Fs6.note
            }            "G6" -> {
            startFrequency = Note.G6.note
            }            "Gs6","Ab6" -> {
            startFrequency = Note.Gs6.note
            }            "A6" -> {
            startFrequency = Note.A6.note
            }            "As6","Bf6" -> {
            startFrequency = Note.As6.note
            }            "B6" -> {
            startFrequency = Note.B6.note
            }            "C7" -> {
                startFrequency = Note.C7.note
            }            "Cs7","Df7" -> {
            startFrequency = Note.Cs7.note
            }            "D7" -> {
            startFrequency = Note.D7.note
            }            "Ds7","Ef7" -> {
            startFrequency = Note.Ds7.note
            }            "E7" -> {
            startFrequency = Note.E7.note
            }            "F7" -> {
            startFrequency = Note.F7.note
            }            "Fs7","Gf7" -> {
            startFrequency = Note.Fs7.note
            }            "G7" -> {
            startFrequency = Note.G7.note
            }            "Gs7","Ab7" -> {
            startFrequency = Note.Gs7.note
            }            "A7" -> {
            startFrequency = Note.A7.note
            }            "As7","Bf7" -> {
            startFrequency = Note.As7.note
            }            "B7" -> {
            startFrequency = Note.B7.note
            }
        }
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
        oos.writeObject(null)
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
        try{
            while(true) {
                buff = (ois.readObject() as ShortArray)
                play_CD.add(buff)
                Log.d("test5", buff.get(0).toString())
                }
        } catch(e: NullPointerException) {
                ois.close()
                return play_CD
        }
    }
}
