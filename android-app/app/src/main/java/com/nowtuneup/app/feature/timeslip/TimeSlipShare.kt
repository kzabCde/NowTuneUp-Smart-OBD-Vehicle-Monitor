package com.nowtuneup.app.feature.timeslip

import android.content.Context
import android.content.Intent

/** Simple text sharing only; advanced image rendering was removed from the 1.8.1 UX. */
object TimeSlipShare {
    fun shareText(context: Context, value: String, mimeType: String = "text/plain") {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_TEXT, value)
                },
                "แชร์ผล Time Slip",
            ),
        )
    }
}
