package com.example.den.koclock

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.example.den.koclock.databinding.FragmentConnectBinding
import io.reactivex.disposables.CompositeDisposable

private const val TAG = "ConnectFragment"

class ConnectFragment : Fragment() {
    private lateinit var binding: FragmentConnectBinding
    private var compositeDisposable = CompositeDisposable()

    private lateinit var  pairedListAdapter : ArrayAdapter<String>
    private var pairedDeviceLabels : MutableList<String> = mutableListOf()

    private lateinit var  newDevListAdapter : ArrayAdapter<String>
    private var newDevDeviceLabels : MutableList<String> = mutableListOf()

    private val koClockViewModel: KoClockViewModel by lazy {
        ViewModelProvider(this).get(KoClockViewModel::class.java)
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentConnectBinding.inflate(inflater, container, false)

        pairedListAdapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, pairedDeviceLabels)
        binding.pairedDevList.adapter = pairedListAdapter

        newDevListAdapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, newDevDeviceLabels)
        binding.newDevList.adapter = newDevListAdapter

        val subscribe = koClockViewModel.bluetooth.getPairedDevice().subscribe { result ->
            result.forEach { (s, s2) ->
                pairedDeviceLabels.add("$s - $s2")
                Log.i(TAG, "Paired $s - $s2")
            }
            pairedListAdapter.notifyDataSetChanged()
        }
        compositeDisposable.add(subscribe)

        return binding.root
    }

    override fun onStart() {
        super.onStart()

        binding.pairedDevList.onItemClickListener = AdapterView.OnItemClickListener { _, itemClicked, _, _ ->
            val lstValues: List<String> = (itemClicked as TextView).text.split(" ").map { it.trim() }
            val subscribe1 = koClockViewModel.bluetooth.connectToDevice(lstValues[0]).subscribe(
                {
                    Log.i(TAG, "Connect OK")
                    Toast.makeText(requireActivity(), "Connect OK", Toast.LENGTH_SHORT).show()
                    requireActivity().supportFragmentManager.popBackStack()

                },
                {e->
                    Log.i(TAG, "Connect ERR. $e")
                    Toast.makeText(requireActivity(), "Connect ERR. $e", Toast.LENGTH_SHORT).show()

                }
            )
            compositeDisposable.add(subscribe1)
        }
        binding.newDevList.onItemClickListener = AdapterView.OnItemClickListener { _, itemClicked, _, _ ->
            val lstValues: List<String> = (itemClicked as TextView).text.split(" ").map { it.trim() }
            val subscribe1 = koClockViewModel.bluetooth.connectToDevice(lstValues[0]).subscribe(
                {
                    Log.i(TAG, "Connect OK")
                    Toast.makeText(requireActivity(), "Connect OK", Toast.LENGTH_SHORT).show()
                    requireActivity().supportFragmentManager.popBackStack()
                },
                {e->
                    Log.i(TAG, "Connect ERR. $e")
                    Toast.makeText(requireActivity(), "Connect ERR $e", Toast.LENGTH_SHORT).show()
                }
            )
            compositeDisposable.add(subscribe1)
        }

        binding.discoveryButon.setOnClickListener {
            newDevDeviceLabels.clear()
            newDevListAdapter.notifyDataSetChanged()

            binding.discoveryButon.isEnabled = false
            binding.progressBar.isVisible = true

            val subscribe1 =
                koClockViewModel.bluetooth.getDiscoveryDevices(requireContext()).subscribe(
                    { r ->
                        r.forEach { (s, s2) ->
                            newDevDeviceLabels.add("$s - $s2")
                            newDevListAdapter.notifyDataSetChanged()
                            Log.i(TAG, "Paired $s - $s2")
                        }
                        newDevListAdapter.notifyDataSetChanged()
                    },
                    { e->//Error
                        Log.i(TAG, "Error discovery $e")
                        Toast.makeText(requireActivity(), "Error discovery $e", Toast.LENGTH_SHORT)
                            .show()
                        binding.discoveryButon.isEnabled = true
                        binding.progressBar.isVisible = false
                    },
                    {  //Complete
                        binding.discoveryButon.isEnabled = true
                        binding.progressBar.isVisible = false
                    }
                )
            compositeDisposable.add(subscribe1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.dispose()
    }
}