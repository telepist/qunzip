package qunzip.util

import kotlin.test.*

class WindowsCommandLineTest {

    @Test
    fun `simple args are not quoted`() {
        assertEquals("x", quoteWindowsArg("x"))
        assertEquals("-y", quoteWindowsArg("-y"))
        assertEquals("-sccUTF-8", quoteWindowsArg("-sccUTF-8"))
    }

    @Test
    fun `empty string becomes empty quotes`() {
        assertEquals("\"\"", quoteWindowsArg(""))
    }

    @Test
    fun `args with spaces are wrapped in quotes`() {
        assertEquals("\"C:\\Quick Unzip\\7z.exe\"", quoteWindowsArg("C:\\Quick Unzip\\7z.exe"))
        // password with a space -> a single quoted token 7z parses as one arg
        assertEquals("\"-pmy pass\"", quoteWindowsArg("-pmy pass"))
    }

    @Test
    fun `embedded quote is escaped with an odd run of backslashes`() {
        // pa"ss -> "pa\"ss"
        assertEquals("\"pa\\\"ss\"", quoteWindowsArg("pa\"ss"))
    }

    @Test
    fun `trailing backslash before closing quote is doubled`() {
        // Classic Windows pitfall: C:\my dir\ -> "C:\my dir\\"
        assertEquals("\"C:\\my dir\\\\\"", quoteWindowsArg("C:\\my dir\\"))
    }

    @Test
    fun `interior backslashes not before a quote are left alone`() {
        assertEquals("C:\\a\\b", quoteWindowsArg("C:\\a\\b"))
    }

    @Test
    fun `buildWindowsCommandLine quotes program and args`() {
        val cmd = buildWindowsCommandLine(
            "C:\\Quick Unzip\\7z.exe",
            listOf("x", "C:\\my archive.zip", "-o C:\\out")
        )
        assertEquals("\"C:\\Quick Unzip\\7z.exe\" x \"C:\\my archive.zip\" \"-o C:\\out\"", cmd)
    }
}
