package com.example.den.koclock

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import io.reactivex.*
import io.reactivex.Observable
import io.reactivex.android.MainThreadDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.TimeUnit


private const val TAG = "BLUETOOTH_ADAPTER"

@SuppressLint("MissingPermission")
class BluetoothObject private constructor(context: Context, activity: Activity){
    //private var bluetooth: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter();   //устарело
    private var bluetoothManager : BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var bluetooth = bluetoothManager.adapter

    private var myUUID: UUID = UUID.fromString(UUID_STRING_WELL_KNOWN_SPP)

    private var bluetoothDev : MutableMap<String, String> = mutableMapOf()

    private lateinit var socket : BluetoothSocket
    private lateinit var inStream : InputStream
    private lateinit var outStream : OutputStream

    var connected = false

    init {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission is not granted")
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION), MY_PERMISSIONS_REQUEST_READ_CONTACTS)
        }
        if (!bluetooth.isEnabled) {
            val enableBT = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivityForResult(enableBT, REQUEST_ENABLE_BT)
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val MY_PERMISSIONS_REQUEST_READ_CONTACTS = 100
        private const val UUID_STRING_WELL_KNOWN_SPP = "00001101-0000-1000-8000-00805F9B34FB"

        private var INSTANCE: BluetoothObject? = null

        fun initialize(context: Context, activity: Activity) {
            if (INSTANCE == null) {
                Log.i(TAG, "Init")
                INSTANCE = BluetoothObject(context, activity)
            }
        }
        fun get(): BluetoothObject {
            return INSTANCE ?: throw IllegalStateException("BluetoothObject must be initialized")
        }
    }

//    fun bluetoothEnable () = bluetooth.isEnabled
//
//    fun deviceAddress (): String =  bluetooth.address
//
//    fun deviceName(): String = bluetooth.name

    private fun pairedDevices (): MutableMap<String, String> {
        val devices : MutableMap<String, String> = mutableMapOf()
        val pairedDevices = bluetooth.bondedDevices

        bluetoothDev.clear()
        pairedDevices.forEach { dev->
            devices.put(dev.address,dev.name)
            bluetoothDev.put(dev.address,dev.name)
        }
//        for (device in pairedDevices) {   //тоже что и выше
//            devices.add("${device.name} : ${device.address}" )
//        }
        return devices
    }

    fun getPairedDevice(): Observable<MutableMap<String, String>> {
        //Log.i(TAG, "Get Dev")
        return Observable.fromCallable { pairedDevices() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun getDiscoveryDevices(context: Context) : Observable<Map<String, String>>{
        val myObservable = Observable.create { e: ObservableEmitter<Map<String, String>> ->
            Log.i(TAG, "Create Observable")

            val filter = IntentFilter()
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            filter.addAction(BluetoothDevice.ACTION_FOUND)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)

            bluetoothDev.clear()
            val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.action
                    Log.i(TAG, "Receive: $action")
                    when(action){
                        BluetoothDevice.ACTION_FOUND -> {
                            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            Log.i(TAG, "ACTION_FOUND: ${device?.name} - ${device?.address}")
                            bluetoothDev.put(device?.address.toString(), device?.name ?: "Unknown Name")
                            e.onNext(mapOf("${device?.address}" to (device?.name ?: "Unknown Name")))
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            Log.i(TAG, "ACTION_DISCOVERY_FINISHED")
                            e.onComplete()
                        }
                    }
                }
            }
            context.registerReceiver(mReceiver, filter) // Не забудьте снять регистрацию в onDestroy

            if (bluetooth.isDiscovering) bluetooth.cancelDiscovery()

            if(!bluetooth.startDiscovery()){
                Log.i(TAG, "Disco ERR")
                e.onError(IllegalStateException("Discovery ERROR"))
            }

            e.setDisposable(object : MainThreadDisposable() {
                override fun onDispose() {
                    Log.i(TAG, "unregister Receiver")
                    context.unregisterReceiver(mReceiver)
                }
            })
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())

        return myObservable
    }
    fun connectToDevice (mac: String) : Completable{
        val device = bluetooth.getRemoteDevice(mac)
        val completable = Completable.create { m ->
            try{ socket = device.createRfcommSocketToServiceRecord(myUUID) }
            catch (e: IOException) { m.onError(e) }
            try {
                socket.connect()
                inStream = socket.inputStream
                outStream = socket.outputStream
                connected = true
                m.onComplete()
            }
            catch (e: IOException){
                socket.close()
                m.onError(e)
            }
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())

        return completable
    }

    fun getByteStream() : Flowable<Byte>{
        val flowable = Flowable.create(FlowableOnSubscribe<Byte> {m ->
            while (!m.isCancelled) m.onNext(inStream.read().toByte())
        }, BackpressureStrategy.BUFFER).share()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())

        return flowable
    }

    fun sendCommandWitchResult(command : String, timeout : Long) : Single<String> {
        val myObservable = Single.create { emitter : SingleEmitter<String> ->
            var result = ""
            Log.i(TAG,"Send - $command")
            inStream.skip(inStream.available().toLong())    //очистить буфер перед отправкой комманды. Если я не отправлял команду но модуль на другом конце что-то постал то это будет в буфере, потому очищаю.
            sendString(command)

            while(!emitter.isDisposed){
               result += inStream.read().toChar()
                Log.i(TAG,"Rec $result")
               if (result.contains("\r\n")) {
                   emitter.onSuccess(result)
               }
            }
        }
            .timeout(timeout, TimeUnit.MILLISECONDS)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
        return myObservable
    }
    fun sendByte(data : Int){
        outStream.write(data)
        outStream.flush()
    }
    fun sendString(data : String){
        outStream.write(data.toByteArray())
        outStream.flush()
    }
}