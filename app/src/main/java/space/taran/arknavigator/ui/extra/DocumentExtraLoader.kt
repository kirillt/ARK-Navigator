package dev.arkbuilders.navigator.ui.extra

import android.widget.TextView
import dev.arkbuilders.navigator.R
import space.taran.arklib.domain.meta.Metadata
import dev.arkbuilders.navigator.utils.extensions.textOrGone

object DocumentExtraLoader {
    fun load(document: Metadata.Document, pagesTV: TextView, verbose: Boolean) {
        val pages = document.pages
        if (pages != null) {
            val label = when {
                verbose -> {
                    if (pages == 1) "$pages page"
                    else "$pages pages"
                }
                else -> "$pages"
            }
            pagesTV.textOrGone(label)
        }
    }

    fun loadWithLabel(
        document: Metadata.Document,
        tvPageNumber: TextView
    ) {
        val pages = document.pages
        if (pages != null) {
            tvPageNumber.textOrGone(
                tvPageNumber.context.getString(R.string.doc_page_no_label, pages)
            )
        }
    }
}
