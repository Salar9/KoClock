package com.example.den.koclock

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        BluetoothObject.initialize(this,this)

        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment == null) {
            val mainFragment = MainStationFragment()

            supportFragmentManager
                .beginTransaction()
                .add(R.id.fragment_container, mainFragment) //добавить фрагмент
                .commit()
        }
    }
}