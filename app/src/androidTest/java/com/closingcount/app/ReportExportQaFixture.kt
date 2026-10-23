package com.closingcount.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.local.OnlineLifecycleEntity
import com.closingcount.app.data.local.OnlineOperationEntity
import com.closingcount.app.data.online.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class ReportExportQaFixture(private val context: Context) {
    val args = InstrumentationRegistry.getArguments()
    val origin = requireNotNull(args.getString("ccsQaOrigin"))
    val outlet = requireNotNull(args.getString("ccsQaOutlet"))
    val password = requireNotNull(args.getString("ccsQaPassword"))
    val username = requireNotNull(args.getString("ccsQaEmployee"))
    val api = OnlineApi(OnlineEndpoint(origin, true))
    lateinit var employee: OnlineSession;lateinit var supervisor: OnlineSession;lateinit var admin: OnlineSession;lateinit var colleague: OnlineSession
    lateinit var closingId: String;lateinit var menuId: String;lateinit var raw: String
    val report get() = FrozenClosingReport.parse(raw)
    private val sessions = mutableListOf<OnlineSession>()
    private var restoreMembership: OnlineTeamMember? = null
    suspend fun seed() {
        require(origin == "http://10.0.2.2:4371" && username.matches(Regex("qah[a-z0-9]+\\.rani")))
        suspend fun login(key: String) = api.login(requireNotNull(args.getString(key)), password).also { sessions += it }
        employee=login("ccsQaEmployee");supervisor=login("ccsQaSupervisor");admin=login("ccsQaAdmin");colleague=login("ccsQaColleague")
        val date=java.time.LocalDate.of(2026,8,1).plusDays(counter.getAndIncrement().toLong()).toString()
        val first=api.create(employee,outlet,date);closingId=first.header.closingId;menuId=first.entries.first().menuId
        quantity(4);act(employee,"submit");act(supervisor,"lock")
        raw=api.report(employee,outlet,closingId,1)
        act(supervisor,"reopen","Koreksi QA ekspor");quantity(6)
    }
    suspend fun quantity(value: Int) {
        val current=api.fetch(employee,outlet,closingId);val entry=current.entries.first { it.menuId==menuId }
        api.send(employee,OnlineOperationEntity(operationId=UUID.randomUUID().toString(),scope=employee.scope(outlet),closingId=closingId,
            menuId=menuId,generation=current.header.generation,expectedRevision=entry.serverRevision,quantity=value,status="SENT",errorCode=null))
    }
    private suspend fun act(session: OnlineSession, action: String, reason: String="") {
        val current=api.fetch(session,outlet,closingId)
        api.lifecycle(session,OnlineLifecycleEntity(session.scope(outlet),closingId,UUID.randomUUID().toString(),action,current.header.generation,
            current.header.revision,reason,"SENT",true,null))
    }
    suspend fun revoke() {
        val member=api.member(admin,outlet,employee.identity.id);restoreMembership=member
        changeMembership(member,false)
    }
    private suspend fun changeMembership(member: OnlineTeamMember, enabled: Boolean) {
        val payload=JSONObject().put("accountId",member.id).put("expectedRevision",member.revision).put("role",member.role).put("active",enabled)
        api.sendTeam(admin,OnlineTeamOperation(admin.scope(outlet),UUID.randomUUID().toString(),"UPDATE_MEMBERSHIP",payload.toString(),"SENT",true),null)
    }
    suspend fun close() {
        restoreMembership?.let { changeMembership(api.member(admin,outlet,it.id),it.active) }
        for(session in sessions)runCatching { api.logout(session) }
    }
    companion object { private val counter=AtomicInteger(0) }
}
