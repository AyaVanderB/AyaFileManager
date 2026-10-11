package com.example.ayafilemanager

import com.example.ayafilemanager.ui.explorer.FileOperations
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileOperationsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun copyPreservesSourceAndNestedContents() {
        val source = temp.newFolder("source")
        File(source, "nested").mkdir()
        File(source, "nested/中文.txt").writeText("文件内容")
        val target = temp.newFolder("target")
        FileOperations.paste(source.path, target.path, false)
        assertEquals("文件内容", File(target, "source/nested/中文.txt").readText())
        assertTrue(File(source, "nested/中文.txt").exists())
    }

    @Test fun existingDestinationIsNeverOverwritten() {
        val source = temp.newFile("note.txt").apply { writeText("source") }
        val target = temp.newFolder("target")
        File(target, source.name).writeText("keep")
        assertThrows(IllegalArgumentException::class.java) { FileOperations.paste(source.path, target.path, false) }
        assertEquals("keep", File(target, source.name).readText())
    }

    @Test fun cannotCopyFolderIntoDescendant() {
        val source = temp.newFolder("source")
        val child = File(source, "child").apply { mkdir() }
        assertThrows(IllegalArgumentException::class.java) { FileOperations.paste(source.path, child.path, false) }
        assertFalse(File(child, "source").exists())
    }

    @Test fun moveAndRenamePreserveContents() {
        val source = temp.newFile("old.txt").apply { writeText("hello") }
        FileOperations.rename(source.path, "new.txt")
        val renamed = File(source.parentFile, "new.txt")
        val target = temp.newFolder("target")
        FileOperations.paste(renamed.path, target.path, true)
        assertFalse(renamed.exists())
        assertEquals("hello", File(target, "new.txt").readText())
    }

    @Test fun rejectsPathTraversalAndNameCollisions() {
        listOf("../escape", ".", "..", "a/b", "a\\b", " ").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { FileOperations.createFolder(temp.root.path, name) }
        }
        FileOperations.createFolder(temp.root.path, "新文件夹")
        assertTrue(File(temp.root, "新文件夹").isDirectory)
        assertThrows(IllegalArgumentException::class.java) { FileOperations.createFolder(temp.root.path, "新文件夹") }
    }
}
