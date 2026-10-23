package com.closingcount.app

import androidx.compose.ui.text.TextLayoutResult

/** Paragraph width can be larger than the painted text's allocated size. Inspect actual lines. */
internal fun significantTextOverflow(layout:TextLayoutResult):Boolean{
    if(layout.multiParagraph.didExceedMaxLines)return true
    val lines=0 until layout.lineCount
    return lines.any{layout.getLineLeft(it)< -1.5f||layout.getLineRight(it)>layout.size.width+1.5f||layout.getLineBottom(it)>layout.size.height+1.5f}
}
