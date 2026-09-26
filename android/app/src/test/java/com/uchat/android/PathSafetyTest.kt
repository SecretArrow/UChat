package com.uchat.android

import com.uchat.android.core.fs.PathSafety
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zip-slip / traversal prevention tests (spec #76). */
class PathSafetyTest {

    @Test
    fun rejectsTraversal() {
        assertFalse(PathSafety.isSafeRelativePath("../etc/passwd"))
        assertFalse(PathSafety.isSafeRelativePath("foo/../../bar"))
        assertFalse(PathSafety.isSafeRelativePath(".."))
    }

    @Test
    fun rejectsAbsolute() {
        assertFalse(PathSafety.isSafeRelativePath("/etc/passwd"))
        assertFalse(PathSafety.isDangerousEntryName("safe"))
        assertTrue(PathSafety.isDangerousEntryName("/etc/passwd"))
        assertTrue(PathSafety.isDangerousEntryName("../../../etc/passwd"))
    }

    @Test
    fun rejectsWindowsDrives() {
        assertFalse(PathSafety.isSafeRelativePath("C:\\Windows\\system32"))
        assertFalse(PathSafety.isSafeRelativePath("C:/Windows"))
        assertTrue(PathSafety.isDangerousEntryName("C:/Windows"))
    }

    @Test
    fun acceptsNormalRelativePaths() {
        assertTrue(PathSafety.isSafeRelativePath("home/user/project/file.txt"))
        assertTrue(PathSafety.isSafeRelativePath("single"))
    }

    @Test
    fun safeResolveStaysInsideBase() {
        val base = createTempDir("uchat-base")
        val ok = PathSafety.safeResolve(base, "a/b.txt")
        assertTrue(ok != null && ok.canonicalPath.startsWith(base.canonicalPath))

        val evil = PathSafety.safeResolve(base, "../outside.txt")
        assertNull(evil)
    }

    @Test
    fun namesAreValidated() {
        assertTrue(PathSafety.isSafeName("my-project_1.2"))
        assertFalse(PathSafety.isSafeName("../evil"))
        assertFalse(PathSafety.isSafeName(".hidden"))
        assertFalse(PathSafety.isSafeName(""))
    }
}
