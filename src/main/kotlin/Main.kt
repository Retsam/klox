import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess
import misc.prettyPrint

var hadError = false

fun main(args: Array<String>) {
  if (args.size > 1) {
    println("Usage: klox [script]")
    exitProcess(64)
  } else if (args.size == 1) {
    runFile(args[0])
  } else {
    runPrompt()
  }
}

fun runFile(path: String) {
  val bytes: ByteArray = File(path).readBytes()
  run(String(bytes, Charsets.UTF_8))

  // Indicate an error in the exit code.
  if (hadError) exitProcess(65)
}

fun runPrompt() {
  val reader = InputStreamReader(System.`in`)
  val bufferedReader = reader.buffered()
  while (true) {
    print("> ")
    val line = bufferedReader.readLine() ?: break
    run(line)
    hadError = false
  }
}

fun run(source: String) {
  val scanner = Scanner(source)
  val tokens = scanner.scanTokens()

  // For now, just print the tokens.
  for (token in tokens) {
    println(token.toString())
  }

  val expr = Parser(tokens).parse() ?: return
  println(prettyPrint(expr))
}

private fun report(line: Int, where: String, message: String) {
  println("[line $line] Error $where: $message")
  hadError = true
}

fun tokenError(token: Token, message: String) {
  if (token.type == TokenType.EOF) {
    report(token.line, " at end", message)
  } else {
    report(token.line, " at '${token.lexeme}'", message)
  }
}

fun error(line: Int, message: String) {
  report(line, "", message)
}
