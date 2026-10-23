package com.closingcount.app.data.local

import org.json.JSONObject
import java.time.LocalDate

/** Only pending menu labels survive capture; frozen catalogs/actors are not retained in the packet. */
data class OnlinePendingClosingHeader(val scope: String, val closingId: String, val generation: Long,
    val revision: Long, val state: String, val date: String, val menuNames: Map<String, String>) {
    companion object {
        fun from(source: OnlineClosingEntity, needed: Set<String>): OnlinePendingClosingHeader {
            require(source.snapshotJson.length <= 2 * 1024 * 1024)
            val raw = JSONObject(source.snapshotJson)
            fun text(row: JSONObject, key: String) = row.get(key).let { require(it is String);it }
            require(text(raw,"id")==source.closingId && text(raw,"outletId")==source.scope.substringAfterLast('|'))
            val generation=raw.get("generation");require(generation is Int || generation is Long)
            require((generation as Number).toLong()==source.generation)
            val date=text(raw,"businessDate");val parsed=LocalDate.parse(date)
            require(parsed.year in 1..9999 && parsed.toString()==date)
            val menus=raw.getJSONObject("catalog").getJSONArray("menus");require(menus.length()<=500)
            val seen=mutableSetOf<String>();val names=mutableMapOf<String,String>()
            for(i in 0 until menus.length()) {
                val menu=menus.getJSONObject(i);val id=text(menu,"id");require(seen.add(id))
                if(id in needed) { val name=text(menu,"name");require(name==name.trim()&&name.length in 1..120);names[id]=name }
            }
            require(names.keys==needed)
            return OnlinePendingClosingHeader(source.scope,source.closingId,source.generation,source.revision,source.state,date,names)
        }
    }
}

/** Immutable values captured together by Room; never used to reinsert live operations. */
data class OnlinePendingClosingCapture(
    val header: OnlinePendingClosingHeader,
    val entries: List<OnlineEntryEntity>,
    val operations: List<OnlineOperationEntity>,
    val lifecycle: OnlineLifecycleEntity?,
)
