package com.infinitysolutions.notessync.login

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.contracts.Contract.Companion.MODE_CHANGE_PASSWORD
import com.infinitysolutions.notessync.contracts.Contract.Companion.MODE_LOGIN_TIME_PASSWORD
import com.infinitysolutions.notessync.contracts.Contract.Companion.MODE_NEW_PASSWORD
import com.infinitysolutions.notessync.contracts.Contract.Companion.PASSWORD_MODE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_CODE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ENCRYPTED
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ID
import kotlinx.android.synthetic.main.fragment_password_set.view.*

class PasswordSetFragment : Fragment() {
    private val TAG = "PasswordSetFragment"
    private lateinit var passwordViewModel: PasswordViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_password_set, container, false)
        var userId = ""
        var cloudType = -1
        var passwordMode = -1
        try {
            userId = arguments?.getString(PREF_ID) as String
            cloudType = arguments?.getInt(PREF_CLOUD_TYPE) as Int
            passwordMode = arguments?.getInt(PASSWORD_MODE) as Int
        } catch (ex: TypeCastException) {
            activity?.setResult(RESULT_CANCELED)
            activity?.finish()
        }

        initDataBinding(rootView, userId, cloudType, passwordMode)

        rootView.ps_submit_button.setOnClickListener {
            submitPassword(
                rootView.ps_password_edit_text.text.toString(),
                rootView.ps_again_password_edit_text.text.toString(),
                userId,
                cloudType,
                passwordMode
            )
        }

        if(passwordMode == MODE_LOGIN_TIME_PASSWORD){
            rootView.ps_skip_button.visibility = VISIBLE
            rootView.ps_skip_button.setOnClickListener {
                val prefs = activity?.getSharedPreferences(Contract.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
                val editor = prefs?.edit()
                editor?.putBoolean(PREF_ENCRYPTED, false)
                editor?.putString(PREF_ID, userId)
                editor?.putInt(PREF_CLOUD_TYPE, cloudType)
                editor?.commit()
                activity?.setResult(RESULT_OK)
                activity?.finish()
            }
        }else{
            rootView.ps_skip_button.visibility = GONE
        }

        return rootView
    }

    private fun initDataBinding(rootView: View, userId: String, cloudType: Int, passwordMode: Int) {
        passwordViewModel = ViewModelProvider(activity!!)[PasswordViewModel::class.java]

        passwordViewModel.getLoadingMessage().observe(viewLifecycleOwner, { message ->
            if (message != null) {
                rootView.ps_loading_panel.visibility = VISIBLE
                rootView.ps_input_bar.visibility = GONE
                rootView.ps_loading_message.text = message
                rootView.ps_skip_button.visibility = GONE
            } else {
                rootView.ps_loading_panel.visibility = GONE
                rootView.ps_input_bar.visibility = VISIBLE
                if(passwordMode == MODE_LOGIN_TIME_PASSWORD)
                    rootView.ps_skip_button.visibility = VISIBLE
                else
                    rootView.ps_skip_button.visibility = GONE
            }
        })

        passwordViewModel.getPasswordSetResult().observe(viewLifecycleOwner, { result ->
            if(result != null){
                if(result)
                    finishLogin(rootView.ps_password_edit_text.text.toString(), userId, cloudType)
                else
                    Toast.makeText(activity, getString(R.string.toast_error), LENGTH_SHORT).show()
            }
        })
    }

    private fun finishLogin(password: String, userId: String, cloudType: Int) {
        val prefs = activity?.getSharedPreferences(Contract.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs?.edit()
        editor?.putString(PREF_CODE, password)
        editor?.putBoolean(PREF_ENCRYPTED, true)
        editor?.putString(PREF_ID, userId)
        editor?.putInt(PREF_CLOUD_TYPE, cloudType)
        editor?.commit()
        Toast.makeText(activity, "Success", LENGTH_SHORT).show()
        activity?.setResult(RESULT_OK)
        activity?.finish()
    }

    private fun submitPassword(
        passText: String,
        againPassText: String,
        userId: String,
        cloudType: Int,
        passwordMode: Int
    ) {
        if (passText.isNotEmpty() && againPassText.isNotEmpty()) {
            if (passText == againPassText) {
                if (passwordMode == MODE_NEW_PASSWORD)
                    passwordViewModel.setupPassword(
                        userId,
                        cloudType,
                        passText
                    )
                else if(passwordMode == MODE_CHANGE_PASSWORD) {
                    val prefs = activity?.getSharedPreferences(Contract.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
                    val oldPass = prefs?.getString(PREF_CODE, null) ?: return
                    passwordViewModel.changePassword(userId, cloudType, oldPass, passText)
                }
            } else
                Toast.makeText(
                    activity, getString(R.string.toast_passwords_dont_match),
                    LENGTH_SHORT
                ).show()
        } else {
            Toast.makeText(activity, getString(R.string.enter_password), LENGTH_SHORT)
                .show()
        }
    }
}