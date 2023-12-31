enum class FunctionType {
  NONE,
  FUNCTION,
  METHOD,
  INITIALIZER,
}

enum class ClassType {
  NONE,
  CLASS,
  SUBCLASS,
}

class Resolver(private val locals: MutableMap<Expr, Int>, program: List<Stmt>) {

  private val scopes = ArrayDeque<MutableMap<String, Boolean>>() // true if variable is initialized
  private var currentFunction = FunctionType.NONE
  private var currentClass = ClassType.NONE

  init {
    program.forEach { resolve(it) }
  }

  private fun pushScope(vararg pairs: Pair<String, Boolean>) {
    scopes.addFirst(mutableMapOf(*pairs))
  }
  private fun popScope() {
    scopes.removeFirst()
  }
  private fun declare(name: Token) {
    if (scopes.isEmpty()) return
    val scope = scopes.first()
    if (scope.containsKey(name.lexeme)) {
      tokenError(name, "Already a variable with this name in this scope.")
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

  private fun resolveFunction(expr: Function, type: FunctionType) {
    // The parameters are declared in the inner scope
    pushScope()
    expr.parameters.forEach {
      declare(it)
      define(it)
    }
    val prevFunction = currentFunction
    currentFunction = type
    expr.body.forEach { resolve(it) }
    currentFunction = prevFunction
    popScope()
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
          tokenError(expr.name, "Can't read local variable in its own initializer.")
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
        // Only top-level functions declare variables
        declare(expr.name)
        define(expr.name)

        resolveFunction(expr, FunctionType.FUNCTION)
      }
      is ReturnStmt -> {
        if (currentFunction == FunctionType.NONE) {
          tokenError(expr.keyword, "Can't return from top-level code.")
        }
        if (currentFunction == FunctionType.INITIALIZER && expr.value != null) {
          tokenError(expr.keyword, "Can't return a value from an initializer.")
        }
        expr.value?.let { resolve(it) }
      }
      is ClassStmt -> {
        declare(expr.name)
        define(expr.name)

        val prevClass = currentClass
        currentClass = ClassType.CLASS

        expr.superclass?.let {
          currentClass = ClassType.SUBCLASS
          resolve(it)
          pushScope(Pair("super", true))
        }

        pushScope(Pair("this", true))
        for (method in expr.methods) {
          resolveFunction(
              method,
              if (method.name.lexeme == "init") FunctionType.INITIALIZER else FunctionType.METHOD)
        }
        scopes.removeFirst()
        if (expr.superclass != null) scopes.removeFirst()
        currentClass = prevClass
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
      is Get -> {
        resolve(expr.primary)
      }
      is Grouping -> resolve(expr.expression)
      is Literal -> {}
      is This -> {
        if (currentClass == ClassType.NONE) {
          tokenError(expr.keyword, "Can't use 'this' outside of a class.")
        }
        resolveLocal(expr, expr.keyword)
      }
      is Logical -> {
        resolve(expr.left)
        resolve(expr.right)
      }
      is SetExpr -> {
        resolve(expr.primary)
        resolve(expr.value)
      }
      is Super -> {
        if (currentClass == ClassType.NONE) {
          tokenError(expr.keyword, "Can't use 'super' outside of a class.")
        } else if (currentClass != ClassType.SUBCLASS) {
          tokenError(expr.keyword, "Can't use 'super' in a class with no superclass.")
        }
        resolveLocal(expr, expr.keyword)
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
      is WhileStmt -> {
        resolve(expr.condition)
        resolve(expr.body)
      }
    }
  }
}
