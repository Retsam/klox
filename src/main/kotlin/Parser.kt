class Parser(private val tokens: List<Token>) {
  private var current = 0

  fun parse(): List<Stmt> {
    return try {
      program()
    } catch (e: ParseError) {
      ArrayList()
    }
  }

  private fun isAtEnd(): Boolean {
    return peek().type == TokenType.EOF
  }

  private fun advance() {
    current += 1
  }

  private fun peek(): Token {
    return tokens.getOrNull(current) ?: throw Exception("Unexpected end of input")
  }

  private fun check(type: TokenType): Boolean {
    if (isAtEnd()) return false
    return peek().type == type
  }

  private fun previous(): Token {
    return tokens[current - 1]
  }

  private fun match(vararg types: TokenType): Boolean {
    if (types.contains(peek().type)) {
      advance()
      return true
    }
    return false
  }

  private fun consume(type: TokenType, message: String): Token {
    val token = peek()
    if (type != token.type) {
      throw error(peek(), message)
    }
    advance()
    return token
  }

  // program → (declaration;)*
  private fun program(): List<Stmt> {
    val statements = ArrayList<Stmt>()
    while (!isAtEnd()) {
      declaration()?.let { statements.add(it) }
    }
    return statements
  }

  // declaration → (IDENTIFIER = expression) | statement
  private fun declaration(): Stmt? {
    try {
      if (match(TokenType.VAR)) {
        return varStatement()
      }
      return statement()
    } catch (e: ParseError) {
      if (!isAtEnd()) {
        synchronize()
      }
      return null
    }
  }

  private fun varStatement(): Stmt {
    val id = consume(TokenType.IDENTIFIER, "Expected an identifier")
    val expr =
        if (match(TokenType.EQUAL)) {
          expression()
        } else {
          Literal(null)
        }
    consume(TokenType.SEMICOLON, "Expected ';' after statement")
    return VarStmt(id, expr)
  }

  // statement → "print(" expression ")" | expression
  private fun statement(): Stmt {
    if (match(TokenType.LEFT_BRACE)) {
      return BlockStmt(block())
    }

    val stmt =
        when {
          match(TokenType.PRINT) -> PrintStmt(expression())
          else -> ExpressionStmt(expression())
        }
    consume(TokenType.SEMICOLON, "Expected ';' after statement")
    return stmt
  }

  private fun block(): List<Stmt> {
    val statements = ArrayList<Stmt>()
    while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
      declaration()?.let { statements.add(it) }
    }
    consume(TokenType.RIGHT_BRACE, "Expected '}' after block")
    return statements
  }

  // expression     → expressions ;
  private fun expression(): Expr {
    return expressions()
  }

  // expressions       → (equality  ,)* equality ;
  private fun expressions(): Expr {
    val expr = assignment()
    if (match(TokenType.COMMA)) {
      return Binary(expr, previous(), expressions())
    }
    return expr
  }

  // assignment     → IDENTIFIER "=" assignment
  //               | equality ;
  private fun assignment(): Expr {
    val expr = equality()
    if (match(TokenType.EQUAL)) {
      val equals = previous()
      val value = assignment()
      if (expr is Variable) {
        val name = expr.name
        return Assign(name, value)
      }
      error(equals, "Invalid assignment target.")
    }
    return expr
  }

  // equality       → comparison ( ( "!=" | "==" ) comparison )* ;
  private fun equality(): Expr {
    var expr = comparison()
    while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
      val operator = previous()
      val right = comparison()
      expr = Binary(expr, operator, right)
    }
    return expr
  }

  // comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
  private fun comparison(): Expr {
    var expr = term()
    while (match(
        TokenType.GREATER,
        TokenType.GREATER_EQUAL,
        TokenType.LESS,
        TokenType.LESS_EQUAL,
    )) {
      val operator = previous()
      val right = term()
      expr = Binary(expr, operator, right)
    }
    return expr
  }

  // term           → factor ( ( "-" | "+" ) factor )* ;
  private fun term(): Expr {
    var expr = factor()
    while (match(TokenType.MINUS, TokenType.PLUS)) {
      val operator = previous()
      val right = factor()
      expr = Binary(expr, operator, right)
    }
    return expr
  }

  // factor         → unary ( ( "/" | "*" ) unary )* ;
  private fun factor(): Expr {
    var expr = unary()
    while (match(TokenType.SLASH, TokenType.STAR)) {
      val operator = previous()
      val right = unary()
      expr = Binary(expr, operator, right)
    }
    return expr
  }

  // unary          → ( "!" | "-" ) unary
  //               | primary ;
  private fun unary(): Expr {
    if (match(TokenType.BANG, TokenType.MINUS)) {
      val op = previous()
      val right = unary()
      return Unary(op, right)
    }
    return primary()
  }

  // primary        → NUMBER | STRING | "true" | "false" | "nil"
  //               | "(" expression ")" ;
  private fun primary(): Expr {
    return when {
      match(TokenType.FALSE) -> Literal(false)
      match(TokenType.TRUE) -> Literal(true)
      match(TokenType.NIL) -> Literal(null)
      match(TokenType.NUMBER, TokenType.STRING) -> Literal(previous().literal)
      match(TokenType.IDENTIFIER) -> Variable(previous())
      match(TokenType.LEFT_PAREN) -> {
        val expr = expression()
        consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.")
        Grouping(expr)
      }
      else -> {
        throw error(peek(), "Expected expression.")
      }
    }
  }

  // Error handling
  private class ParseError : Exception()

  private fun error(token: Token, message: String): ParseError {
    tokenError(token, message)
    return ParseError()
  }

  private fun synchronize() {
    advance()
    while (!isAtEnd()) {
      if (previous().type == TokenType.SEMICOLON) return
      when (peek().type) {
        TokenType.CLASS,
        TokenType.FUN,
        TokenType.VAR,
        TokenType.FOR,
        TokenType.IF,
        TokenType.WHILE,
        TokenType.PRINT,
        TokenType.RETURN -> return
        else -> advance()
      }
    }
  }
}
