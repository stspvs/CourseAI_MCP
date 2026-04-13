package ru.courseai.currencywatch.mcp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class McpSandboxPathsTest {

    @Test
    fun resolveAcceptsNestedFile() {
        val root = File.createTempFile("cw_sandbox", "").apply { delete() }.absoluteFile
        root.mkdirs()
        try {
            val dataDir = File(root, "data").apply { mkdirs() }
            when (val r = resolveSandboxFile(dataDir, "a/b/c.txt")) {
                is SandboxPathResult.Err -> throw AssertionError(r.message)
                is SandboxPathResult.Ok -> {
                    assertTrue(r.file.absolutePath.contains("a${File.separator}b"))
                    assertEquals(File(dataDir, "a/b/c.txt").canonicalFile, r.file)
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolveRejectsTraversalOutside() {
        val root = File.createTempFile("cw_sandbox2", "").apply { delete() }.absoluteFile
        root.mkdirs()
        try {
            val dataDir = File(root, "data").apply { mkdirs() }
            val outside = File(root, "outside").apply { mkdirs() }
            File(outside, "secret.txt").writeText("x")

            val escape = "../../outside/secret.txt"
            val r = resolveSandboxFile(dataDir, escape)
            assertIs<SandboxPathResult.Err>(r)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolveRejectsAbsolutePath() {
        val dataDir = File.createTempFile("cw_sandbox3", "").apply { delete() }.absoluteFile
        dataDir.mkdirs()
        try {
            val r = resolveSandboxFile(dataDir, dataDir.absolutePath)
            assertIs<SandboxPathResult.Err>(r)
        } finally {
            dataDir.deleteRecursively()
        }
    }

    @Test
    fun resolveRejectsEmptyPath() {
        val dataDir = File.createTempFile("cw_sandbox4", "").apply { delete() }.absoluteFile
        dataDir.mkdirs()
        try {
            val r = resolveSandboxFile(dataDir, "   ")
            assertIs<SandboxPathResult.Err>(r)
        } finally {
            dataDir.deleteRecursively()
        }
    }
}
