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

  private fun withSemicolon(stmt: Stmt): Stmt {
    consume(TokenType.SEMICOLON, "Expected ';' after statement")
    return stmt
  }

  // declaration → (IDENTIFIER = expression) | statement
  private fun declaration(): Stmt? {
    try {
      if (match(TokenType.VAR)) {
        return withSemicolon(varStatement())
      }
      if (match(TokenType.FUN)) {
        return function("function")
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
    return VarStmt(id, expr)
  }

  private fun function(kind: String): Stmt {
    val name = consume(TokenType.IDENTIFIER, "Expected $kind name")
    consume(TokenType.LEFT_PAREN, "Expected '(' after $kind name")
    val parameters = ArrayList<Token>()
    if (!check(TokenType.RIGHT_PAREN)) {
      do {
        if (parameters.size >= 255) {
          error(peek(), "Can't have more than 255 parameters.")
        }
        parameters.add(consume(TokenType.IDENTIFIER, "Expected parameter name"))
      } while (match(TokenType.COMMA))
    }
    consume(TokenType.RIGHT_PAREN, "Expected ')' after parameters")
    consume(TokenType.LEFT_BRACE, "Expected '{' before $kind body")
    val body = block()
    return Function(name, parameters, body)
  }

  // statement → "print(" expression ")" | expression
  private fun statement(): Stmt {
    if (match(TokenType.LEFT_BRACE)) {
      return BlockStmt(block())
    }

    return when {
      match(TokenType.PRINT) -> withSemicolon(PrintStmt(expression()))
      match(TokenType.IF) -> ifStatement()
      match(TokenType.WHILE) -> whileStatement()
      match(TokenType.FOR) -> forStatement()
      match(TokenType.RETURN) -> {
        withSemicolon(ReturnStmt(previous(), expression()))
      }
      else -> withSemicolon(ExpressionStmt(expression()))
    }
  }

  private fun block(): List<Stmt> {
    val statements = ArrayList<Stmt>()
    while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
      declaration()?.let { statements.add(it) }
    }
    consume(TokenType.RIGHT_BRACE, "Expected '}' after block")
    return statements
  }

  private fun ifStatement(): IfStmt {
    consume(TokenType.LEFT_PAREN, "Expected '(' after 'if'")
    val condition = expression()
    consume(TokenType.RIGHT_PAREN, "Expected ')' after if condition")

    val thenBranch = statement()
    val elseBranch =
        if (match(TokenType.ELSE)) {
          statement()
        } else {
          null
        }
    return IfStmt(condition, thenBranch, elseBranch)
  }

  private fun whileStatement(): WhileStmt {
    consume(TokenType.LEFT_PAREN, "Expected '(' after 'while'")
    val condition = expression()
    consume(TokenType.RIGHT_PAREN, "Expected ')' after while condition")
    val body = statement()
    return WhileStmt(condition, body)
  }

  private fun forStatement(): Stmt {
    consume(TokenType.LEFT_PAREN, "Expected '(' after 'for'")
    val init =
        when (peek().type) {
          TokenType.SEMICOLON -> null
          TokenType.VAR -> {
            advance()
            varStatement()
          }
          else -> ExpressionStmt(expression())
        }
    consume(TokenType.SEMICOLON, "Expected ';' after for initializer")

    var condition: Expr? = null
    if (!check(TokenType.SEMICOLON)) {
      condition = expression()
    }
    consume(TokenType.SEMICOLON, "Expected ';' after for condition")

    var increment: Expr? = null
    if (!check(TokenType.SEMICOLON)) {
      increment = expression()
    }
    consume(TokenType.RIGHT_PAREN, "Expected ')' after for clauses")
    val body = statement()

    // Constructing the sugar for while loops
    var whileBody = body
    if (increment != null) {
      whileBody = BlockStmt(listOf(body, ExpressionStmt(increment)))
    }

    val loopStatement = WhileStmt(condition ?: Literal(true), whileBody)
    return if (init != null) {
      BlockStmt(listOf(init, loopStatement))
    } else {
      loopStatement
    }
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
  //               | logic_or ;
  private fun assignment(): Expr {
    val expr = logicOr()
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

  // logic_or       → logic_and ( "or" logic_and )* ;
  private fun logicOr(): Expr {
    var expr = logicAnd()
    while (match(TokenType.OR)) {
      val operator = previous()
      val right = logicAnd()
      expr = Logical(expr, operator, right)
    }
    return expr
  }

  // logic_and      → equality ( "and" equality )* ;
  private fun logicAnd(): Expr {
    var expr = equality()
    while (match(TokenType.AND)) {
      val operator = previous()
      val right = equality()
      expr = Logical(expr, operator, right)
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

  // unary          → ( "!" | "-" ) unary | call
  private fun unary(): Expr {
    if (match(TokenType.BANG, TokenType.MINUS)) {
      val op = previous()
      val right = unary()
      return Unary(op, right)
    }

    return call()
  }

  // call           → primary ( "(" arguments? ")" )* ;
  private fun call(): Expr {
    var expr = primary()
    while (true) {
      if (match(TokenType.LEFT_PAREN)) {
        expr = finishCall(expr)
      } else {
        break
      }
    }
    return expr
  }

  private fun finishCall(primary: Expr): Expr {
    val arguments = ArrayList<Expr>()
    if (!check(TokenType.RIGHT_PAREN)) {
      do {
        // allow trailing comma
        if (peek().type == TokenType.RIGHT_PAREN) {
          break
        }
        if (arguments.size >= 255) {
          error(peek(), "Can't have more than 255 arguments.")
        }
        // Bypassing expression/expressions to avoid the comma operator
        arguments.add(assignment())
      } while (match(TokenType.COMMA))
    }
    val paren = consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.")
    return FunctionCall(primary, paren, arguments)
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
