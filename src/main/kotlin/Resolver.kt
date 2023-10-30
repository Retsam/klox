enum class FunctionType {
  NONE,
  FUNCTION,
}

class Resolver(private val locals: MutableMap<Expr, Int>, program: List<Stmt>) {

  private val scopes = ArrayDeque<MutableMap<String, Boolean>>() // true if variable is initialized
  private var currentFunction = FunctionType.NONE

  init {
    program.forEach { resolve(it) }
  }

  private fun pushScope() {
    scopes.addFirst(mutableMapOf())
  }
  private fun popScope() {
    scopes.removeFirst()
  }
  private fun declare(name: Token) {
    if (scopes.isEmpty()) return
    val scope = scopes.first()
    if (scope.containsKey(name.lexeme)) {
      tokenError(name, "Variable with this name already declared in this scope.")
    }
    scope[name.lexeme] = false
  }
  private fun define(name: Token) {
    if (scopes.isEmpty()) return
    val scope = scopes.first()
    assert(scope.containsKey(name.lexeme))
    scope[name.lexeme] = true
  }

  private fun resolveLocal(expr: Expr, name: Token) {
    for (i in 0 until scopes.size) {
      if (scopes[i].containsKey(name.lexeme)) {
        locals[expr] = i
        return
      }
    }
    // Not found. Assume it is global.
  }

  private fun resolve(expr: StmtExpr) {
    when (expr) {
      // interesting cases
      is Assign -> {
        resolve(expr.value)
        resolveLocal(expr, expr.name)
      }
      is Variable -> {
        if (!scopes.isEmpty() && scopes.last()[expr.name.lexeme] == false) {
          tokenError(expr.name, "Cannot read local variable in its own initializer.")
        }
        resolveLocal(expr, expr.name)
      }
      is VarStmt -> {
        declare(expr.identifier)
        resolve(expr.expr)
        define(expr.identifier)
      }
      is BlockStmt -> {
        pushScope()
        expr.statements.forEach { resolve(it) }
        popScope()
      }
      is Function -> {
        // The function itself is declared in the outer scope
        declare(expr.name)
        define(expr.name)

        // The parameters are declared in the inner scope
        pushScope()
        expr.parameters.forEach {
          declare(it)
          define(it)
        }
        val prevFunction = currentFunction
        currentFunction = FunctionType.FUNCTION
        expr.body.forEach { resolve(it) }
        currentFunction = prevFunction
        popScope()
      }
      is ReturnStmt -> {
        if (currentFunction == FunctionType.NONE) {
          tokenError(expr.keyword, "Cannot return from top-level code.")
        }
        expr.value?.let { resolve(it) }
      }
      is ClassStmt -> {
        declare(expr.name)
        define(expr.name)
      }

      // no interesting logic, just tree walking
      is Binary -> {
        resolve(expr.left)
        resolve(expr.right)
      }
      is FunctionCall -> {
        resolve(expr.primary)
        expr.arguments.forEach { resolve(it) }
      }
      is Grouping -> resolve(expr.expression)
      is Literal -> {}
      is Logical -> {
        resolve(expr.left)
        resolve(expr.right)
      }
      is Unary -> resolve(expr.right)

      // Statements
      is ExpressionStmt -> resolve(expr.expr)
      is IfStmt -> {
        resolve(expr.condition)
        resolve(expr.thenBranch)
        expr.elseBranch?.let { resolve(it) }
      }
      is PrintStmt -> resolve(expr.expr)
      is WhileStmt -> resolve(expr.body)
    }
  }
}
