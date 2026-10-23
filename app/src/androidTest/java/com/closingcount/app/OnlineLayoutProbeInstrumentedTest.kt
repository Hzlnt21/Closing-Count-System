package com.closingcount.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OnlineLayoutProbeInstrumentedTest{
    @get:Rule val compose=createComposeRule()
    @Test fun actualLineBoundsAcceptFittingTextAndDetectClippingOrEllipsis(){
        compose.setContent{MaterialTheme{Column(Modifier.width(250.dp)){
            Text("Username",modifier=Modifier.testTag("fit"))
            Text("A deliberately long label that cannot fit here",modifier=Modifier.width(40.dp).height(12.dp).testTag("clip"),maxLines=1,overflow=TextOverflow.Clip)
            Text("A deliberately long label that must be ellipsized",modifier=Modifier.width(40.dp).testTag("ellipsis"),maxLines=1,overflow=TextOverflow.Ellipsis)
        }}}
        fun result(tag:String):TextLayoutResult{val list=mutableListOf<TextLayoutResult>();compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(list)};return list.single()}
        assertFalse(significantTextOverflow(result("fit")))
        assertTrue(significantTextOverflow(result("clip")))
        val truncated=result("ellipsis");assertTrue(significantTextOverflow(truncated));assertTrue(truncated.isLineEllipsized(0))
    }
}
