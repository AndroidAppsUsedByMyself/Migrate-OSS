package balti.migrate.backupEngines.containers

import balti.migrate.utilities.CommonToolKotlin.Companion.FILE_FILE_LIST
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

data class ZipAppBatch(val zipPackets: ArrayList<ZipAppPacket> = ArrayList(0),
                       var containerDirectoryName: String) {
    var batchSystemSize : Long = 0
    var batchDataSize : Long = 0
    var zipFullSize : Long = 0
    val extrasFiles: ArrayList<File> = ArrayList(0)
    var fileList: File? = null
    init {
        zipPackets.forEach {
            batchDataSize += it.appPacket_z.dataSize
            batchSystemSize += it.appPacket_z.systemSize
            zipFullSize += it.zipPacketSize
        }
    }
    fun addExtras(extras: ArrayList<File>){
        extrasFiles.clear()
        extrasFiles.addAll(extras)
        extrasFiles.forEach {
            batchDataSize += it.length() / 1024
            zipFullSize += it.length() / 1024
        }
    }
    fun createFileList(){
        val fl = File(containerDirectoryName, FILE_FILE_LIST)

        fl.delete()
        BufferedWriter(FileWriter(fl)).run {
            zipPackets.forEach { zp ->
                zp.appFiles.forEach { af ->
                    write("${af.name}\n")
                }
            }
            extrasFiles.forEach {ef ->
                write("${ef.name}\n")
            }
        }

        fileList = fl
    }
}