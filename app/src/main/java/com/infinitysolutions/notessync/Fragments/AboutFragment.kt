package com.infinitysolutions.notessync.Fragments


import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
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
        toolbar.title = "About"
        toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
        }

        rootView.rate_button.setOnClickListener {
            openLink("https://play.google.com/store/apps/details?id=com.infinitysolutions.notessync")
        }

        rootView.report_bugs_button.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)
            val to = arrayOf("infinitysolutionsv1.1@gmail.com")
            intent.type = "vnd.android.cursor.dir/email"
            intent.putExtra(Intent.EXTRA_EMAIL, to)
            intent.putExtra(Intent.EXTRA_SUBJECT, "[Notes Sync][v1.0]")
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
                .setTitle("Changelog")
                .setMessage("VERSION 2.0 JANUARY 20, 2020\n\n - Add images to any note\n - Added compact view mode\n - Added make a copy option\n - Share images to and from Notes Sync\n - Added option to empty trash\n - Changed app icon\n - Fixed syncing error\n - Auto-Sync now works instantly\n - Fixed a lot of bugs\n - Performance improvements\n\n" +
                            "VERSION 1.7 SEPTEMBER 6, 2019\n\n - Fixed issue with title not shown in some notes in widget\n\n" +
                            "VERSION 1.6 SEPTEMBER 4, 2019\n\n - Added AMOLED Theme\n - Fixed bugs\n - Added colored notes to widget\n - Improved widget performance\n - Added drag and drop in Todo list\n - Checked items move to the bottom\n\n" +
                            "VERSION 1.5 AUGUST 4, 2019\n\n - Added encrypted sync\n - Added app lock\n - More efficient auto sync\n\n" +
                            "VERSION 1.3 JULY 25, 2019\n\n - Reduced size\n\n" +
                            "VERSION 1.2 JULY 25,2019\n\n - Improved Performance\n\n" +
                            "VERSION 1.1 JULY 24,2019\n\n - Added widget\n\n" +
                            "VERSION 1.0  JULY 23,2019\n\n - Initial release"
                )
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun openLink(link: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        if (browserIntent.resolveActivity(activity!!.packageManager) != null)
            startActivity(browserIntent)
        else
            Toast.makeText(activity, "No browser found!", Toast.LENGTH_SHORT).show()
    }
}
