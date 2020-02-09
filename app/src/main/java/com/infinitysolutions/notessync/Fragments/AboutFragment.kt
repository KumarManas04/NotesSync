package com.infinitysolutions.notessync.Fragments


import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.infinitysolutions.notessync.BuildConfig
import kotlinx.android.synthetic.main.fragment_about.*
import kotlinx.android.synthetic.main.fragment_about.view.*
import kotlinx.android.synthetic.main.fragment_settings.view.toolbar


class AboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(
            com.infinitysolutions.notessync.R.layout.fragment_about,
            container,
            false
        )
        setupView(rootView)
        return rootView
    }

    private fun setupView(rootView: View) {
        val toolbar = rootView.toolbar
        toolbar.title = getString(com.infinitysolutions.notessync.R.string.about)
        toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
        }
        rootView.app_version.setText(BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")")
        rootView.rate_button.setOnClickListener {
            openLink("https://play.google.com/store/apps/details?id=com.infinitysolutions.notessync")
        }

        rootView.report_bugs_button.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)
            val to = arrayOf("infinitysolutionsv1.1@gmail.com")
            intent.type = "vnd.android.cursor.dir/email"
            intent.putExtra(Intent.EXTRA_EMAIL, to)
            intent.putExtra(Intent.EXTRA_SUBJECT, "[Notes Sync][v"+BuildConfig.VERSION_NAME+"]")
            intent.putExtra(Intent.EXTRA_TEXT, "Your problems, suggestions, requests...")
            startActivity(Intent.createChooser(intent, "Send Email"))
        }

        rootView.translate_button.setOnClickListener {
            openLink("https://oschmmt.oneskyapp.com/collaboration/project?id=161514")
        }

        rootView.more_apps_button.setOnClickListener {
            openLink("https://play.google.com/store/apps/developer?id=Kumar+Manas")
        }

        rootView.changelog_button.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle(getString(com.infinitysolutions.notessync.R.string.changelog))
                .setMessage(Html.fromHtml(getString(com.infinitysolutions.notessync.R.string.changelog_details)
                , Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM))
                .setPositiveButton( getString(com.infinitysolutions.notessync.R.string.close), null)
                .show()
        }
    }

    private fun openLink(link: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        if (browserIntent.resolveActivity(activity!!.packageManager) != null)
            startActivity(browserIntent)
        else
            Toast.makeText(activity, getString(com.infinitysolutions.notessync.R.string.toast_no_browser), Toast.LENGTH_SHORT).show()
    }
}
