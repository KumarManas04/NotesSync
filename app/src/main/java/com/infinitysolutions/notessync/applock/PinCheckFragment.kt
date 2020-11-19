package com.infinitysolutions.notessync.applock


import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.navigation.fragment.NavHostFragment.findNavController
import com.infinitysolutions.notessync.contracts.Contract.Companion.APP_LOCK_STATE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_APP_LOCK_CODE
import com.infinitysolutions.notessync.contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.contracts.Contract.Companion.STATE_NEW_PIN
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.contracts.Contract.Companion.STATE_CHANGE_PIN
import com.infinitysolutions.notessync.contracts.Contract.Companion.STATE_CHECK_PIN
import com.infinitysolutions.notessync.contracts.Contract.Companion.STATE_MAIN_PIN
import com.infinitysolutions.notessync.contracts.Contract.Companion.STATE_NOTE_EDIT
import kotlinx.android.synthetic.main.fragment_app_lock.view.*

class PinCheckFragment : Fragment() {
    private val TAG = "PinCheckFragment"
    private val passCode = MutableLiveData<String>()

    init {
        passCode.value = ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_app_lock, container, false)

        val type = arguments?.getInt(APP_LOCK_STATE) ?: STATE_CHECK_PIN
        initEntryPin(rootView, type)

        passCode.observe(this, { code ->
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
                    submitCode(code, type)
                }
            }
        })
        return rootView
    }

    private fun initEntryPin(rootView: View, type: Int) {
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs != null) {
            if (prefs.contains(PREF_APP_LOCK_CODE))
                prepareButtons(rootView)
            else
                nextScreen(type)
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

    private fun nextScreen(type: Int){
        when(type){
            STATE_NEW_PIN, STATE_CHANGE_PIN -> findNavController(this).navigate(R.id.action_pinCheckFragment_to_pinChangeFragment)
            STATE_MAIN_PIN -> findNavController(this).navigate(R.id.action_appLockFragment_to_mainFragment)
            STATE_NOTE_EDIT -> findNavController(this).navigate(R.id.action_appLockFragment2_to_noteEditFragment2)
            else ->{
                activity?.finish()
            }
        }
    }

    private fun submitCode(code: String, type: Int) {
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs != null) {
            val pin = prefs.getString(PREF_APP_LOCK_CODE, null)
            if (pin != null && pin == code) {
                nextScreen(type)
            }else{
                Toast.makeText(activity, getString(R.string.toast_pin_incorrect), LENGTH_SHORT).show()
                passCode.value = ""
            }
        }
    }
}