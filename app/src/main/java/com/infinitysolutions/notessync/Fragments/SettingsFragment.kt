package com.infinitysolutions.notessync.Fragments


import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_THEME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.R
import kotlinx.android.synthetic.main.fragment_settings.view.*

class SettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_settings, container, false)
        setupViews(rootView)
        return rootView
    }

    private fun setupViews(rootView: View) {
        val toolbar = rootView.toolbar
        toolbar.title = "Settings"
        toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
        }

        val nightModeToggle = rootView.night_mode_toggle
        val sharedPrefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (sharedPrefs!!.contains(PREF_THEME))
            nightModeToggle.isChecked = sharedPrefs.getInt(PREF_THEME, 0) == 1

        nightModeToggle.setOnCheckedChangeListener { buttonView, isChecked ->
            val editor = sharedPrefs.edit()
            if (isChecked)
                editor.putInt(PREF_THEME, 1)
            else
                editor.putInt(PREF_THEME, 0)

            editor.commit()
            activity?.recreate()
        }

        rootView.night_mode_button.setOnClickListener {
            nightModeToggle.toggle()
        }

        rootView.lock_button.setOnClickListener {
            Navigation.findNavController(rootView).navigate(R.id.action_settingsFragment_to_lockFragment)
        }

        rootView.about_button.setOnClickListener {
            Navigation.findNavController(rootView).navigate(R.id.action_settingsFragment_to_aboutFragment)
        }

        rootView.open_source_button.setOnClickListener {
            Navigation.findNavController(rootView).navigate(R.id.action_settingsFragment_to_openSourceFragment)
        }
    }
}
