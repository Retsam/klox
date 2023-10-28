import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ScriptTests {

  @BeforeEach
  fun cleanUp() {
    reset()
    testMode = true
  }

  private fun run(name: String): String {
    val outputStream = ByteArrayOutputStream()
    val stdout = System.out
    System.setOut(PrintStream(outputStream))

    runFile("lox/$name")
    System.setOut(stdout)
    return outputStream.toString()
  }
  private fun runFile(name: String, expectedOut: String) {
    val out = run(name)

    assertFalse(hadError)
    assertFalse(hadRuntimeError)

    assertEquals(expectedOut, out)
  }
  private fun runFileWithError(name: String, expectedOut: String) {
    val out = run(name)

    assertEquals(expectedOut, out)
  }

  @Test
  fun helloWorld() {
    runFile("hello_world.lox", "Hello, world!\n")
  }

  @Test
  fun scoping() {
    runFile(
        "scoping.lox",
        """
          inner a
          outer b
          global c
          outer a
          outer b
          global c
          global a
          global b
          global c"""
            .trimIndent() + "\n")
  }

  @Test
  fun fib() {
    runFile(
        "fib.lox",
        """
              0
              1
              1
              2
              3
              5
              8
              13
              21
              34
              55
              89
              144
              233
              377
              610
              987"""
            .trimIndent() + "\n")
  }

  @Test
  fun counter() {
    runFile("counter.lox", "1\n2\n")
  }
  @Test
  fun resolution() {
    runFile("resolution.lox", "global\nglobal\n")
  }

  @Test
  fun resolve_error() {
    runFileWithError(
        "resolve_error.lox",
        "[line 3] Error at 'a': Variable with this name already declared in this scope.\n" +
            "[line 6] Error at 'return': Cannot return from top-level code.\n")
  }
}
