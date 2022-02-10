package com.example.den.koclock

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.den.koclock.databinding.FragmentMainStationBinding
import io.reactivex.disposables.CompositeDisposable
import java.text.SimpleDateFormat
import java.util.*


private const val TAG = "MainStationFragment"
private const val TIMEOUT : Long = 200

class MainStationFragment : Fragment() {
    private lateinit var binding: FragmentMainStationBinding
    private var compositeDisposable = CompositeDisposable()


    //привязка ViewModel к фрагменту
    private val koClockViewModel: KoClockViewModel by lazy {//by lazy говорит инициализировать переменную при первом обращении к ней
        ViewModelProvider(this).get(KoClockViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMainStationBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.connectButton.setOnClickListener {
            connect()
        }
        binding.timeSyncButton.setOnClickListener {
            //отправляем комманду синхронизации времени
            //берем смещение часового пояса от GMT прибавляем его к текущему времени и приобразовываетм в строку HEX
            val mCalendar : Calendar = GregorianCalendar()
            val mTimeZone = mCalendar.timeZone
            val mGMTOffset = mTimeZone.rawOffset

            val setTimeCmd = "$1 ${Integer.toHexString((System.currentTimeMillis() / 1000L + mGMTOffset / 1000).toInt())} \r\n" //пробел перед \r\n обязателен, такое требование прошивки часов
            sendCmd(setTimeCmd)
        }
        binding.saveButton.setOnClickListener {
            val setSettingsCmd = "$2 ${binding.DayLevel.text} ${binding.NightLevel.text} ${binding.NightStart.text} ${binding.NightEnd.text} \r\n"  //пробел перед \r\n обязателен, такое требование прошивки часов
            sendCmd(setSettingsCmd)
        }
    }

    override fun onStart() {
        super.onStart()
        if(koClockViewModel.bluetooth.connected){
            binding.saveButton.isEnabled = true
            binding.timeSyncButton.isEnabled = true
            binding.connectButton.isEnabled = false
            val subscribe =
                koClockViewModel.bluetooth.sendCommandWitchResult("$3 \r\n", TIMEOUT).subscribe(
                    { r ->
                        Log.i(TAG, "Result received - $r")
                        val settings: List<String> = r.split(" ").map { it.trim() }
                        binding.DayLevel.setText(settings[0])
                        binding.NightLevel.setText(settings[1])
                        binding.NightStart.setText(convertTime(settings[2]))
                        binding.NightEnd.setText(convertTime(settings[3]))
                        //Toast.makeText(requireActivity(), "Result received - $r", Toast.LENGTH_SHORT).show()
                    },
                    { e ->
                        Log.i(TAG, "Error $e")
                        Toast.makeText(requireActivity(), "Error $e", Toast.LENGTH_SHORT).show()
                    }
                )
            compositeDisposable.add(subscribe)
        }
    }
    override fun onAttach(context: Context){
        super.onAttach(context)
        connect()
    }

    private fun sendCmd(cmd : String){
        val subscribe = koClockViewModel.bluetooth.sendCommandWitchResult(cmd, TIMEOUT).subscribe(
            { r ->
                Log.i(TAG, "Result received - $r")
                Toast.makeText(requireActivity(), "Result received - $r", Toast.LENGTH_SHORT).show()
            },
            { e ->
                Log.i(TAG, "Error $e")
                Toast.makeText(requireActivity(), "Error $e", Toast.LENGTH_SHORT).show()
            }
        )
        compositeDisposable.add(subscribe)
    }

    private fun connect(){
        if(!koClockViewModel.bluetooth.connected){
            Log.d(TAG, "Not connected")
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ConnectFragment())
                .addToBackStack(null)
                .commit()
        }
        else{
            Toast.makeText(requireActivity(), "Already connected", Toast.LENGTH_SHORT).show()
        }
    }
    private fun convertTime(time : String) : String{
        val format = SimpleDateFormat("HH:mm")
        format.timeZone = TimeZone.getTimeZone("GMT-0")
        val formattedDate = format.format(Date(time.toLong() * 1000L))
        Log.i("TAG", "Formatted date - $formattedDate")
        return formattedDate
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG,"Count subscribers - ${compositeDisposable.size()}")
        compositeDisposable.dispose()
    }

}