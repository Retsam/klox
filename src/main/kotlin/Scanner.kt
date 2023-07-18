class Scanner(val code: String) {
  private var start = 0
  private var current = 0
  private var line = 1
  private val tokens = mutableListOf<Token>()
  fun scanTokens(): List<Token> {
    while (!isAtEnd()) {
      start = current
      scanToken()
    }
    start = current
    addToken(TokenType.EOF)
    return tokens
  }

  private fun isAtEnd() = current >= code.length
  private fun advance() = code[current++]

  private fun peek(): Char {
    if (isAtEnd()) return '\u0000'
    return code[current]
  }

  private fun match(char: Char): Boolean {
    if (peek() == char) {
      current++
      return true
    }
    return false
  }

  private fun addToken(type: TokenType, literal: Any? = null) {
    tokens += Token(type, code.substring(start, current), literal, line)
  }

  private fun scanToken() {
    val char = advance()
    when (char) {
      '(' -> addToken(TokenType.LEFT_PAREN)
      ')' -> addToken(TokenType.RIGHT_PAREN)
      '{' -> addToken(TokenType.LEFT_BRACE)
      '}' -> addToken(TokenType.RIGHT_BRACE)
      ',' -> addToken(TokenType.COMMA)
      '.' -> addToken(TokenType.DOT)
      '-' -> addToken(TokenType.MINUS)
      '+' -> addToken(TokenType.PLUS)
      ';' -> addToken(TokenType.SEMICOLON)
      '*' -> addToken(TokenType.STAR)
      '!' -> addToken(if (match('=')) TokenType.BANG_EQUAL else TokenType.BANG)
      '=' -> addToken(if (match('=')) TokenType.EQUAL_EQUAL else TokenType.EQUAL)
      '<' -> addToken(if (match('=')) TokenType.LESS_EQUAL else TokenType.LESS)
      '>' -> addToken(if (match('=')) TokenType.GREATER_EQUAL else TokenType.GREATER)
      '/' -> {
        if (match('/')) {
          while (peek() != '\n' && !isAtEnd()) advance()
          return
        }
        addToken(TokenType.SLASH)
      }
      ' ',
      '\r',
      '\t' -> {
        return
      }
      '\n' -> {
        line++
        return
      }
      '"' -> string()
      in '0'..'9' -> number()
      else -> {
        if (isAlpha(char)) {
          while (!isAtEnd() && isAlphaNumeric(peek())) {
            advance()
          }
          val lexeme = code.substring(start, current)
          asKeywordToken(lexeme)?.let { tokens += it } ?: addToken(TokenType.IDENTIFIER)
        }
      }
    }
  }

  private fun string() {
    while (!isAtEnd()) {
      when (val ch = advance()) {
        '"' -> {
          addToken(TokenType.STRING, code.substring(start, current))
          return
        }
        '\n' -> {
          line++
        }
      }
    }
    error(line, "Unterminated string.")
  }

  private fun number() {
    while (!isAtEnd()) {
      val ch = peek()
      if (ch in '0'..'9' || ch == '.') {
        advance()
        continue
      }
      break
    }
    val numVal = code.substring(start, current).toDoubleOrNull()
    if (numVal == null) {
      error(line, "Invalid number.")
    }
    // In case of an error still add a zero number - this should help keep the structure more
    // accurate later in the parse
    addToken(TokenType.NUMBER, numVal ?: 0.0)
  }

  private fun isAlpha(char: Char): Boolean {
    return char in 'a'..'z' || char in 'A'..'Z' || char == '_'
  }
  private fun isDigit(char: Char): Boolean {
    return char in '0'..'9'
  }
  private fun isAlphaNumeric(char: Char): Boolean {
    return isAlpha(char) || isDigit(char)
  }

  private fun asKeywordToken(lexeme: String): Token? {
    fun asToken(type: TokenType) = Token(type, lexeme, null, line)
    return when (lexeme) {
      "and" -> asToken(TokenType.AND)
      "class" -> asToken(TokenType.CLASS)
      "else" -> asToken(TokenType.ELSE)
      "false" -> Token(TokenType.FALSE, lexeme, false, line)
      "for" -> asToken(TokenType.FOR)
      "fun" -> asToken(TokenType.FUN)
      "if" -> asToken(TokenType.IF)
      "nil" -> Token(TokenType.NIL, lexeme, null, line)
      "or" -> asToken(TokenType.OR)
      "print" -> asToken(TokenType.PRINT)
      "return" -> asToken(TokenType.RETURN)
      "super" -> asToken(TokenType.SUPER)
      "this" -> asToken(TokenType.THIS)
      "true" -> Token(TokenType.TRUE, lexeme, true, line)
      "var" -> asToken(TokenType.VAR)
      "while" -> asToken(TokenType.WHILE)
      else -> null
    }
  }
}

class Token(val type: TokenType, val lexeme: String, val literal: Any?, val line: Int) {
  override fun toString(): String {
    return "$type $lexeme $literal"
  }
}

enum class TokenType {
  // Single-character tokens.
  LEFT_PAREN,
  RIGHT_PAREN,
  LEFT_BRACE,
  RIGHT_BRACE,
  COMMA,
  DOT,
  MINUS,
  PLUS,
  SEMICOLON,
  SLASH,
  STAR,

  // One or two character tokens.
  BANG,
  BANG_EQUAL,
  EQUAL,
  EQUAL_EQUAL,
  GREATER,
  GREATER_EQUAL,
  LESS,
  LESS_EQUAL,

  // Literals.
  IDENTIFIER,
  STRING,
  NUMBER,

  // Keywords.
  AND,
  CLASS,
  ELSE,
  FALSE,
  FUN,
  FOR,
  IF,
  NIL,
  OR,
  PRINT,
  RETURN,
  SUPER,
  THIS,
  TRUE,
  VAR,
  WHILE,
  EOF
}
