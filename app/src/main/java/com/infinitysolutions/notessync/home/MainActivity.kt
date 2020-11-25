package com.infinitysolutions.notessync.home

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.get
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.contracts.Contract.Companion.APP_LOCK_STATE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_THEME
import com.infinitysolutions.notessync.contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.contracts.Contract.Companion.STATE_MAIN_PIN
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_AMOLED
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_DARK
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_DEFAULT
import com.infinitysolutions.notessync.settings.SettingsActivity
import com.infinitysolutions.notessync.util.WorkSchedulerHelper
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.support_development_dialog.view.*

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs.contains(PREF_THEME)) {
            when (prefs.getInt(PREF_THEME, THEME_DEFAULT)) {
                THEME_DEFAULT -> setTheme(R.style.AppTheme)
                THEME_DARK -> setTheme(R.style.AppThemeDark)
                THEME_AMOLED -> setTheme(R.style.AppThemeAmoled)
            }
        }

        WorkSchedulerHelper().setAutoDelete(this)
        setContentView(R.layout.activity_main)

        val bundle = Bundle()
        bundle.putInt(APP_LOCK_STATE, STATE_MAIN_PIN)
        findNavController(R.id.nav_host_fragment).setGraph(R.navigation.home_nav_graph, bundle)
        initDataBinding()
    }

    private fun initDataBinding() {
        val homeViewModel = ViewModelProviders.of(this).get(HomeViewModel::class.java)

        homeViewModel.getToolbar().observe(this, { toolbar ->
            if (toolbar != null) {
                val toggle = ActionBarDrawerToggle(
                    this,
                    drawer_layout,
                    toolbar,
                    R.string.app_name,
                    R.string.app_name
                )
                drawer_layout.addDrawerListener(toggle)
                toggle.isDrawerIndicatorEnabled = true
                drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                toggle.syncState()
                prepareNavDrawer()
            }else{
                drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
        })

        Navigation.findNavController(this, R.id.nav_host_fragment)
            .addOnDestinationChangedListener { _, destination, _ ->
                when (destination.id) {
                    R.id.mainFragment -> {
                        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                        //Hide keyboard
                        val view = this.currentFocus
                        view?.let { v ->
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.hideSoftInputFromWindow(v.windowToken, 0)
                        }
                    }
                    else -> drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }
            }
    }

    private fun prepareNavDrawer() {
        val homeViewModel = ViewModelProviders.of(this).get(HomeViewModel::class.java)
        val index = homeViewModel.getViewMode().value
        if (index != null)
            navigation_view.menu[index - 1].isChecked = true
        else
            navigation_view.menu[0].isChecked = true
        navigation_view.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.notes -> {
                    homeViewModel.setViewMode(1)
                    drawer_layout.closeDrawers()
                }
                R.id.archive -> {
                    homeViewModel.setViewMode(2)
                    drawer_layout.closeDrawers()
                }
                R.id.trash -> {
                    homeViewModel.setViewMode(3)
                    drawer_layout.closeDrawers()
                }
                R.id.settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                R.id.support_development ->{
                    drawer_layout.closeDrawers()
                    val prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
                    val dialogBuilder = when (prefs.getInt(PREF_THEME, THEME_DEFAULT)) {
                        THEME_DEFAULT -> AlertDialog.Builder(this, R.style.LightDialogStyle)
                        THEME_DARK -> AlertDialog.Builder(this, R.style.DarkDialogStyle)
                        THEME_AMOLED -> AlertDialog.Builder(this, R.style.AmoledDialogStyle)
                        else->{
                            AlertDialog.Builder(this)
                        }
                    }
                    val inflater = getSystemService(LAYOUT_INFLATER_SERVICE ) as LayoutInflater
                    val dialogView = inflater.inflate(R.layout.support_development_dialog, null)
                    dialogView.iced_tea.setOnClickListener {
                        openLink("https://rzp.io/i/AS8eWQZ")
                    }
                    dialogView.coffee.setOnClickListener {
                        openLink("https://rzp.io/i/93dGQCN")
                    }
                    dialogView.apple_cider.setOnClickListener {
                        openLink("https://rzp.io/i/DBerESE")
                    }
                    dialogView.meal.setOnClickListener {
                        openLink("https://rzp.io/i/hhlPjyz")
                    }
                    dialogView.premium_meal.setOnClickListener {
                        openLink("https://rzp.io/i/h5K7T9q")
                    }
                    dialogBuilder.setView(dialogView)
                    dialogBuilder.create().show()
                }
                R.id.share -> {
                    val message =
                        "Hey there!\nTry Notes Sync.\nIt is really fast, easy to use and privacy focused with lots of cool features. https://play.google.com/store/apps/details?id=com.infinitysolutions.notessync"
                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.type = "text/plain"
                    shareIntent.putExtra(Intent.EXTRA_TEXT, message)
                    startActivity(Intent.createChooser(shareIntent, "Share..."))
                }
                R.id.rate -> {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=com.infinitysolutions.notessync")
                    )
                    if (browserIntent.resolveActivity(packageManager) != null)
                        startActivity(browserIntent)
                    else
                        Toast.makeText(this, getString(R.string.toast_no_browser), LENGTH_SHORT).show()
                }
                R.id.about -> {
                    Navigation.findNavController(this, R.id.nav_host_fragment)
                        .navigate(R.id.action_mainFragment_to_aboutFragment)
                    drawer_layout.closeDrawers()
                }
            }
            true
        }
    }

    private fun openLink(link: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        if (browserIntent.resolveActivity(packageManager) != null)
            startActivity(browserIntent)
        else
            Toast.makeText(this, getString(R.string.toast_no_browser), LENGTH_SHORT).show()
    }
}