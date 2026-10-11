package com.example.ayafilemanager.ui.explorer

import java.io.File

/** Local operations never replace an existing destination. */
internal object FileOperations {
    private fun validName(name: String): String {
        require(name.isNotBlank() && name == name.trim() && name !in listOf(".", "..") &&
            name.none { it == '/' || it == '\\' || it == '\u0000' }) { "请输入有效名称，不包含路径分隔符" }
        return name
    }

    fun createFolder(directory: String, name: String): String {
        val target = File(directory, validName(name))
        require(!target.exists()) { "同名文件已存在" }
        check(target.mkdir()) { "无法新建文件夹，请检查权限" }
        return "已新建 ${target.name}"
    }

    fun rename(path: String, name: String): String {
        val source = File(path)
        val target = File(source.parentFile, validName(name))
        require(!target.exists()) { "同名文件已存在" }
        check(source.renameTo(target)) { "重命名失败，请检查权限" }
        return "已重命名为 ${target.name}"
    }

    fun paste(path: String, directory: String, move: Boolean): String {
        val source = File(path).canonicalFile
        val target = File(directory, source.name).canonicalFile
        require(source.exists() && source.canRead()) { "源文件不存在或不可读" }
        require(!target.exists()) { "目标已有同名文件，请先重命名" }
        require(!target.toPath().startsWith(source.toPath())) { "不能粘贴到自身或子目录" }
        if (move) {
            check(source.renameTo(target)) { "移动失败：请检查权限，跨存储移动暂不支持" }
        } else {
            // Reserve the destination before writing, so cleanup only touches our own output.
            check(if (source.isDirectory) target.mkdir() else target.createNewFile()) { "无法创建目标文件" }
            try {
                if (source.isDirectory) copyChildren(source, target) else source.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                target.deleteRecursively()
                throw e
            }
        }
        return if (move) "已移动 ${source.name}" else "已复制 ${source.name}"
    }

    private fun copyChildren(source: File, target: File) {
        val children = source.listFiles() ?: error("无法读取文件夹：${source.name}")
        children.forEach { child ->
            require(child.canonicalFile.toPath() == child.absoluteFile.toPath().normalize()) { "不支持复制符号链接" }
            val destination = File(target, child.name)
            if (child.isDirectory) {
                check(destination.mkdir()) { "无法创建文件夹：${child.name}" }
                copyChildren(child, destination)
            } else child.copyTo(destination, overwrite = false)
        }
    }
}
