package com.infinitysolutions.notessync.fragments


import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.NavHostFragment.findNavController
import com.infinitysolutions.notessync.contracts.Contract.Companion.APP_LOCK_STATE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_APP_LOCK_CODE
import com.infinitysolutions.notessync.contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.contracts.Contract.Companion.STATE_NEW_PIN
import com.infinitysolutions.notessync.R
import kotlinx.android.synthetic.main.fragment_app_lock.view.*

class AppLockFragment : Fragment() {
    private val TAG = "AppLockFragment"
    private val passCode = MutableLiveData<String>()
    private var pass2 = ""

    init {
        passCode.value = ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_app_lock, container, false)

        val type = arguments?.getInt(APP_LOCK_STATE)
        if (type != null) {
            if (type == STATE_NEW_PIN) {
                rootView.message_text_view.text = getString(R.string.enter_new_pin)
                prepareButtons(rootView)
            } else {
                rootView.message_text_view.text = getString(R.string.enter_old_pin)
                prepareButtons(rootView)
            }
        } else {
            initEntryPin(rootView)
        }

        passCode.observe(this, Observer { code ->
            when (code.length) {
                0 -> {
                    rootView.indicator_1.setImageResource(R.drawable.circle_stroke)
                    rootView.indicator_2.setImageResource(R.drawable.circle_stroke)
                    rootView.indicator_3.setImageResource(R.drawable.circle_stroke)
                    rootView.indicator_4.setImageResource(R.drawable.circle_stroke)
                }
                1 -> {
                    rootView.indicator_1.setImageResource(R.drawable.round_color)
                }
                2 -> {
                    rootView.indicator_2.setImageResource(R.drawable.round_color)
                }
                3 -> {
                    rootView.indicator_3.setImageResource(R.drawable.round_color)
                }
                4 -> {
                    rootView.indicator_4.setImageResource(R.drawable.round_color)
                    submitCode(rootView, code)
                }
            }
        })
        return rootView
    }

    private fun initEntryPin(rootView: View) {
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs != null) {
            if (prefs.contains(PREF_APP_LOCK_CODE)) {
                prepareButtons(rootView)
            } else {
                findNavController(this).navigate(R.id.action_appLockFragment_to_mainFragment)
            }
        }
    }

    private fun prepareButtons(rootView: View) {
        rootView.btn_1.setOnClickListener {
            enterKey("1")
        }
        rootView.btn_2.setOnClickListener {
            enterKey("2")
        }
        rootView.btn_3.setOnClickListener {
            enterKey("3")
        }
        rootView.btn_4.setOnClickListener {
            enterKey("4")
        }
        rootView.btn_5.setOnClickListener {
            enterKey("5")
        }
        rootView.btn_6.setOnClickListener {
            enterKey("6")
        }
        rootView.btn_7.setOnClickListener {
            enterKey("7")
        }
        rootView.btn_8.setOnClickListener {
            enterKey("8")
        }
        rootView.btn_9.setOnClickListener {
            enterKey("9")
        }
        rootView.btn_0.setOnClickListener {
            enterKey("0")
        }
        rootView.clear_button.setOnClickListener {
            passCode.value = ""
        }
    }

    private fun enterKey(input: String) {
        if (passCode.value!!.length < 4) {
            passCode.value += input
        }
    }

    private fun submitCode(rootView: View, code: String) {
        val type = arguments?.getInt(APP_LOCK_STATE)
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (type != null) {
            if (type == STATE_NEW_PIN) {
                if (pass2 == "") {
                    pass2 = code
                    passCode.value = ""
                    rootView.message_text_view.text = getString(R.string.re_enter_pin)
                } else {
                    if (code == pass2) {
                        if (prefs != null) {
                            prefs.edit().putString(PREF_APP_LOCK_CODE, code).commit()
                            activity?.onBackPressed()
                        }
                    } else {
                        Toast.makeText(activity, getString(R.string.toast_pins_dont_match), LENGTH_SHORT).show()
                        passCode.value = ""
                        pass2 = ""
                        rootView.message_text_view.text = getString(R.string.enter_new_pin)
                    }
                }
            } else {
                if (pass2 == "") {
                    if (prefs != null) {
                        val oldPIN = prefs.getString(PREF_APP_LOCK_CODE, null)
                        if (oldPIN == code) {
                            pass2 = code
                            passCode.value = ""
                            rootView.message_text_view.text = getString(R.string.enter_new_pin)
                        } else {
                            Toast.makeText(activity, getString(R.string.toast_old_pins_incorrect), LENGTH_SHORT).show()
                            passCode.value = ""
                        }
                    }
                } else {
                    prefs?.edit()?.putString(PREF_APP_LOCK_CODE, code)?.apply()
                    activity?.onBackPressed()
                }
            }
        } else {
            if (prefs != null) {
                val pin = prefs.getString(PREF_APP_LOCK_CODE, null)
                if (pin != null && pin == code) {
                    findNavController(this).navigate(R.id.action_appLockFragment_to_mainFragment)
                }else{
                    Toast.makeText(activity, getString(R.string.toast_pin_incorrect), LENGTH_SHORT).show()
                    passCode.value = ""
                }
            }
        }
    }
}