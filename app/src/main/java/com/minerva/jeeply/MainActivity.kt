package com.minerva.jeeply

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.minerva.jeeply.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Initialize the bottom navigation view
        //create bottom navigation view object
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNavigationView.setOnItemSelectedListener { item ->
            // By using switch we can easily get
            // the selected fragment
            // by using there id.
            var selectedFragment: Fragment? = null
            when (item.itemId) {
                R.id.dashboard -> {
                    selectedFragment = DashboardFragment()
                }
                R.id.routes -> {
                    selectedFragment = RoutesFragment()
                }
                R.id.journeys -> {
                    selectedFragment = ThirdFragment()
                }
            }
            // It will help to replace the
            // one fragment to other.
            if (selectedFragment != null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.nav_fragment, selectedFragment).commit()
            }

            return@setOnItemSelectedListener true
        }

        // as soon as the application opens the first
        // fragment should be shown to the user
        // in this case it is algorithm fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_fragment, DashboardFragment()).commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}