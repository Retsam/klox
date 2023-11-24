import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess
import misc.prettyPrint

var hadError = false
var hadRuntimeError = false
var debug = false
var testMode = false

var interpreter = Interpreter()

fun reset() {
  hadError = false
  hadRuntimeError = false

  interpreter = Interpreter()
}

fun main(argsIn: Array<String>) {
  var args = argsIn.toList()
  if (args.find { it == "--debug" } != null) {
    debug = true
    args = args.filter { it != "--debug" }
  }
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
  run(File(path).readText())

  // Indicate an error in the exit code.
  if (hadError && !testMode) exitProcess(65)
  if (hadRuntimeError && !testMode) exitProcess(70)
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

  val statements = Parser(tokens).parse()

  if (debug) statements.forEach { println(prettyPrint(it)) }
  else {
    interpreter.runResolver(statements)
    if (hadError) return
    interpreter.interpret(statements)
  }
}

private fun report(line: Int, where: String, message: String) {
  System.err.println("[line $line] Error$where: $message")
  hadError = true
}

fun tokenError(token: Token, message: String) {
  if (token.type == TokenType.EOF) {
    report(token.line, " at end", message)
  } else {
    report(token.line, " at '${token.lexeme}'", message)
  }
}

fun runtimeError(error: Interpreter.RuntimeError) {
  System.err.println(error.message + "\n[line " + error.token.line + "]")
  hadRuntimeError = true
}

fun error(line: Int, message: String) {
  report(line, "", message)
}
