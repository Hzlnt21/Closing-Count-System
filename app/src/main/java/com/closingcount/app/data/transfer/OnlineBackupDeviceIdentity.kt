package com.closingcount.app.data.transfer

import android.content.Context
import android.util.AtomicFile
import com.closingcount.app.data.online.OnlineApi
import java.io.File
import java.util.UUID

/** Pseudonymous installation identity; never a hardware ID or authenticated grant. */
class OnlineBackupDeviceIdentity(context:Context) {
    private val file=AtomicFile(File(context.noBackupFilesDir,"online-backup-device-id"))
    fun get():String = synchronized(lock) {
        fun read():String=file.openRead().use { input ->
            val bytes=ByteArray(37);var count=0
            while(count<bytes.size){val n=input.read(bytes,count,bytes.size-count);if(n<0)break;check(n>0);count+=n}
            check(count==36);OnlineApi.uuid(String(bytes,0,count,Charsets.US_ASCII))
        }
        if(file.baseFile.exists()||File(file.baseFile.path+".bak").exists())return@synchronized read()
        val id=UUID.randomUUID().toString();val out=file.startWrite()
        try{out.write(id.toByteArray(Charsets.US_ASCII));file.finishWrite(out)}catch(error:Exception){file.failWrite(out);throw error}
        check(read()==id);id
    }
    companion object {private val lock=Any()}
}
