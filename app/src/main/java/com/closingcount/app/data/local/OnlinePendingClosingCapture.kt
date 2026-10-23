package com.closingcount.app.data.local

/** Immutable values captured together by Room; never used to reinsert live operations. */
data class OnlinePendingClosingCapture(
    val header: OnlineClosingEntity,
    val entries: List<OnlineEntryEntity>,
    val operations: List<OnlineOperationEntity>,
    val lifecycle: OnlineLifecycleEntity?,
)
