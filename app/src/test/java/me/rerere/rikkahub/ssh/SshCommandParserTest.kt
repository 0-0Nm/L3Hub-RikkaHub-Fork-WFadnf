package me.rerere.rikkahub.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SshCommandParserTest {
    @Test
    fun `blank lines are ignored`() {
        assertEquals(SshParseResult.Blank, SshCommandParser.parse(""))
        assertEquals(SshParseResult.Blank, SshCommandParser.parse("   "))
    }

    @Test
    fun `commands without arguments parse`() {
        assertEquals(SshParseResult.Valid(SshCommand.Help), SshCommandParser.parse("help"))
        assertEquals(SshParseResult.Valid(SshCommand.Conversations), SshCommandParser.parse("conversations"))
        assertEquals(SshParseResult.Valid(SshCommand.Models), SshCommandParser.parse("models"))
        assertEquals(SshParseResult.Valid(SshCommand.WhoAmI), SshCommandParser.parse("whoami"))
        assertEquals(SshParseResult.Valid(SshCommand.Exit), SshCommandParser.parse("exit"))
    }

    @Test
    fun `command names are case insensitive and tolerate padding`() {
        assertEquals(SshParseResult.Valid(SshCommand.Models), SshCommandParser.parse("  MODELS  "))
        assertEquals(SshParseResult.Valid(SshCommand.Help), SshCommandParser.parse("Help"))
    }

    @Test
    fun `send keeps the message text verbatim`() {
        assertEquals(
            SshParseResult.Valid(SshCommand.Send("id-1", "hello world")),
            SshCommandParser.parse("send id-1 hello world  "),
        )
        assertEquals(
            SshParseResult.Valid(SshCommand.Send("id-1", "a  b")),
            SshCommandParser.parse("send   id-1    a  b"),
        )
    }

    @Test
    fun `send without text reports usage`() {
        val result = SshCommandParser.parse("send id-1")
        assertTrue(result is SshParseResult.Invalid)
        assertEquals(SshCommandParser.USAGE_SEND, (result as SshParseResult.Invalid).message)
    }

    @Test
    fun `send without arguments reports usage`() {
        assertEquals(
            SshParseResult.Invalid(SshCommandParser.USAGE_SEND),
            SshCommandParser.parse("send"),
        )
        assertEquals(
            SshParseResult.Invalid(SshCommandParser.USAGE_SEND),
            SshCommandParser.parse("send    "),
        )
    }

    @Test
    fun `tail without id reports usage`() {
        assertEquals(
            SshParseResult.Invalid(SshCommandParser.USAGE_TAIL),
            SshCommandParser.parse("tail"),
        )
        assertEquals(
            SshParseResult.Invalid(SshCommandParser.USAGE_TAIL),
            SshCommandParser.parse("tail   "),
        )
        assertEquals(
            SshParseResult.Valid(SshCommand.Tail("id-2")),
            SshCommandParser.parse("tail id-2"),
        )
    }

    @Test
    fun `unknown command is rejected`() {
        val result = SshCommandParser.parse("frobnicate now")
        assertTrue(result is SshParseResult.Invalid)
        val message = (result as SshParseResult.Invalid).message
        assertTrue(message.startsWith("unknown command: frobnicate"))
    }
}
