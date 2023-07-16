import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess

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
}

fun runPrompt() {
  val reader = InputStreamReader(System.`in`)
  val bufferedReader = reader.buffered()
  while (true) {
    print("> ")
    val line = bufferedReader.readLine() ?: break
    run(line)
  }
}

fun run(code: String) {
  println("Should execute `$code`")
}
