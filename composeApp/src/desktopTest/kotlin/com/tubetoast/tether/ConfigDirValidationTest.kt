package com.tubetoast.tether

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigDirValidationTest {
    private lateinit var tmpDir: java.io.File

    @BeforeTest
    fun setup() {
        tmpDir = Files.createTempDirectory("tether-configdir-validation-test").toFile()
    }

    @AfterTest
    fun teardown() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `non-existent path under writable parent is usable and gets created`() {
        val fresh = tmpDir.resolve("fresh-identity")
        assertFalse(fresh.exists())
        assertTrue(isUsableConfigDir(fresh))
        assertTrue(fresh.isDirectory)
    }

    @Test
    fun `existing regular file is not usable`() {
        val file = tmpDir.resolve("not-a-dir.txt").also { it.createNewFile() }
        assertFalse(isUsableConfigDir(file))
    }

    @Test
    fun `existing writable directory is usable`() {
        val dir = tmpDir.resolve("existing-dir").also { it.mkdir() }
        assertTrue(isUsableConfigDir(dir))
    }
}
