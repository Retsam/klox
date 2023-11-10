class Environment(private val enclosure: Environment? = null) {
  private val values = HashMap<String, Any?>()

  fun assign(name: String, value: Any?): Environment {
    values[name] = value
    return this
  }

  fun assignAt(distance: Int, name: String, value: Any?) {
    var environment = this
    for (i in 0 until distance) {
      assert(
          environment.enclosure != null,
      ) {
        "Assertion failed: invalid distance resolving variable."
      }
      environment = environment.enclosure!!
    }
    assert(environment.values.containsKey(name)) {
      "Assertion failed: invalid name resolving variable."
    }
    environment.values[name] = value
  }

  fun get(token: Token): Any? {
    val name = token.lexeme
    if (values.containsKey(name)) {
      return values[name]
    }
    throw Interpreter.RuntimeError(token, "Undefined variable '${name}'.")
  }

  fun getAt(distance: Int, name: String): Any? {
    var environment = this
    for (i in 0 until distance) {
      assert(
          environment.enclosure != null,
      ) {
        "Assertion failed: invalid distance resolving variable."
      }
      environment = environment.enclosure!!
    }
    assert(environment.values.containsKey(name)) {
      "Assertion failed: invalid name resolving variable."
    }
    return environment.values[name]
  }
}

interface LoxCallable {
  fun call(interpreter: Interpreter, arguments: List<Any?>): Any?

  fun arity(): Int
}

class Return(val value: Any?) : RuntimeException(null, null, false, false)

class LoxCallableFunction(
    private val func: Function,
    private val enclosure: Environment,
    private val isInitializer: Boolean
) : LoxCallable {
  override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
    val scope = interpreter.environment
    try {
      interpreter.environment = Environment(enclosure)
      for (i in func.parameters.indices) {
        interpreter.environment.assign(func.parameters[i].lexeme, arguments[i])
      }
      interpreter.interpret(func.body)
    } catch (e: Return) {
      if (isInitializer) return enclosure.getAt(0, "this")
      return e.value
    } finally {
      interpreter.environment = scope
    }

    return null
  }

  override fun arity(): Int {
    return func.parameters.size
  }

  override fun toString(): String {
    return "<fn ${func.name.lexeme}>"
  }

  fun bind(instance: LoxInstance): LoxCallableFunction {
    val environment = Environment(enclosure)
    environment.assign("this", instance)
    return LoxCallableFunction(func, environment, isInitializer)
  }
}

class LoxInstance(private val clazz: LoxClass) {
  val fields = HashMap<String, Any?>()

  fun get(name: Token): Any? {
    val key = name.lexeme
    if (fields.containsKey(key)) {
      return fields[key]
    }
    if (clazz.methods.containsKey(key)) {
      return clazz.methods[key]?.bind(this)
    }
    throw Interpreter.RuntimeError(name, "Undefined property '${key}'.")
  }

  override fun toString(): String {
    return "<instance ${clazz.name.lexeme}>"
  }
}

class LoxClass(val name: Token, val methods: HashMap<String, LoxCallableFunction>) : LoxCallable {
  override fun call(interpreter: Interpreter, arguments: List<Any?>): LoxInstance {
    val instance = LoxInstance(this)
    val initializer = methods["init"]
    initializer?.bind(instance)?.call(interpreter, arguments)
    return instance
  }

  override fun arity(): Int {
    val initializer = methods["init"]
    if (initializer != null) {
      return initializer.arity()
    }
    return 0
  }

  override fun toString(): String {
    return "<class ${name.lexeme}>"
  }
}

class Interpreter {
  var environment =
      Environment()
          .assign(
              "clock",
              object : LoxCallable {
                override fun call(interpreter: Interpreter, arguments: List<Any?>): Any {
                  return System.currentTimeMillis() / 1000.0
                }

                override fun arity(): Int {
                  return 0
                }

                override fun toString(): String {
                  return "<native fn>"
                }
              },
          )
  private var globals = environment

  // Maps from the expression to the distance from the current scope
  private val locals: MutableMap<Expr, Int> = HashMap()

  private fun lookupVariable(name: Token, expr: Expr): Any? {
    return when (val distance = locals[expr]) {
      null -> globals.get(name)
      else -> environment.getAt(distance, name.lexeme)
    }
  }

  fun runResolver(program: List<Stmt>) {
    Resolver(locals, program)
  }

  fun interpret(statements: List<Stmt>) {
    try {
      for (stmt in statements) {
        execute(stmt)
      }
    } catch (e: RuntimeError) {
      runtimeError(e)
    }
  }

  private fun stringify(obj: Any?): String {
    return when (obj) {
      null -> "nil"
      is Double -> {
        val str = obj.toString()
        return if (str.endsWith(".0")) {
          str.substring(0, str.length - 2)
        } else {
          str
        }
      }
      else -> obj.toString()
    }
  }

  private fun execute(stmt: Stmt) {
    when (stmt) {
      is BlockStmt -> {
        val scope = environment
        try {
          environment = Environment(environment)
          for (statement in stmt.statements) {
            execute(statement)
          }
        } finally {
          environment = scope
        }
      }
      is ClassStmt -> {
        val methods = HashMap<String, LoxCallableFunction>()
        for (method in stmt.methods) {
          methods[method.name.lexeme] =
              LoxCallableFunction(method, environment, method.name.lexeme == "init")
        }
        environment.assign(stmt.name.lexeme, LoxClass(stmt.name, methods))
      }
      is ExpressionStmt -> {
        evaluate(stmt.expr)
      }
      is Function -> {
        environment.assign(stmt.name.lexeme, LoxCallableFunction(stmt, environment, false))
      }
      is IfStmt -> {
        if (isTruthy(evaluate(stmt.condition))) {
          execute(stmt.thenBranch)
        } else {
          stmt.elseBranch?.let { execute(it) }
        }
      }
      is PrintStmt -> {
        val value = evaluate(stmt.expr)
        println(stringify(value))
      }
      is ReturnStmt -> {
        throw Return(stmt.value?.let { evaluate(it) })
      }
      is VarStmt -> {
        val value = evaluate(stmt.expr)
        environment.assign(stmt.identifier.lexeme, value)
      }
      is WhileStmt -> {
        while (isTruthy(evaluate(stmt.condition))) {
          execute(stmt.body)
        }
      }
    }
  }

  private fun evaluate(expr: Expr): Any? {
    return when (expr) {
      is Assign -> {
        val value = evaluate(expr.value)
        when (val distance = locals[expr]) {
          null -> globals.assign(expr.name.lexeme, value)
          else -> environment.assignAt(distance, expr.name.lexeme, value)
        }
        value
      }
      is Binary -> binaryOperation(evaluate(expr.left), expr.operator, evaluate(expr.right))
      is FunctionCall -> {
        val callee = evaluate(expr.primary)
        val arguments = expr.arguments.map { evaluate(it) }
        if (callee !is LoxCallable) {
          throw RuntimeError(expr.paren, "Can only call functions and classes.")
        }
        if (arguments.size != callee.arity()) {
          throw RuntimeError(
              expr.paren,
              "Expected ${callee.arity()} arguments but got ${arguments.size}.",
          )
        }
        return callee.call(this, arguments)
      }
      is Get -> {
        val lhs = evaluate(expr.primary)
        if (lhs !is LoxInstance) {
          throw RuntimeError(expr.name, "Only instances have properties.")
        }
        return lhs.get(expr.name)
      }
      is Grouping -> evaluate(expr.expression)
      is Literal -> expr.value
      is Logical -> logicalOperation(expr)
      is SetExpr -> {
        val lhs = evaluate(expr.primary)
        if (lhs !is LoxInstance) {
          throw RuntimeError(expr.name, "Only instances have fields.")
        }
        val value = evaluate(expr.value)
        lhs.fields[expr.name.lexeme] = value
        value
      }
      is This -> {
        lookupVariable(expr.keyword, expr)
      }
      is Variable -> lookupVariable(expr.name, expr)
      is Unary -> unaryOperation(expr.operator, evaluate(expr.right))
    }
  }

  private fun binaryOperation(left: Any?, operator: Token, right: Any?): Any? {
    return when (operator.type) {
      TokenType.MINUS -> {
        checkNumericOperand(operator, left) - checkNumericOperand(operator, right)
      }
      TokenType.SLASH -> {
        checkNumericOperand(operator, left) / checkNumericOperand(operator, right)
      }
      TokenType.STAR -> {
        checkNumericOperand(operator, left) * checkNumericOperand(operator, right)
      }
      TokenType.PLUS -> {
        when {
          left is Double && right is Double -> left + right
          left is String && right is String -> left + right
          else -> throw RuntimeError(operator, "Operands must be two numbers or two strings.")
        }
      }
      TokenType.GREATER -> {
        checkNumericOperand(operator, left) > checkNumericOperand(operator, right)
      }
      TokenType.GREATER_EQUAL -> {
        checkNumericOperand(operator, left) >= checkNumericOperand(operator, right)
      }
      TokenType.LESS -> {
        checkNumericOperand(operator, left) < checkNumericOperand(operator, right)
      }
      TokenType.LESS_EQUAL -> {
        checkNumericOperand(operator, left) <= checkNumericOperand(operator, right)
      }
      TokenType.EQUAL_EQUAL -> {
        left == right
      }
      TokenType.BANG_EQUAL -> {
        left != right
      }
      TokenType.COMMA -> {
        right
      }
      else -> {
        throw RuntimeError(operator, "Binary operator not supported. ${operator.lexeme}")
      }
    }
  }

  private fun logicalOperation(expr: Logical): Any? {
    val left = evaluate(expr.left)
    if (expr.operator.type == TokenType.OR) {
      if (isTruthy(left)) return left
    } else {
      if (!isTruthy(left)) return left
    }
    return evaluate(expr.right)
  }

  private fun unaryOperation(operator: Token, right: Any?): Any {
    return when (operator.type) {
      TokenType.MINUS -> {
        -checkNumericOperand(operator, right)
      }
      TokenType.BANG -> {
        !isTruthy(right)
      }
      else -> {
        throw RuntimeError(operator, "Unary operator not supported.")
      }
    }
  }

  private fun checkNumericOperand(operator: Token, operand: Any?): Double {
    if (operand is Double) return operand
    throw RuntimeError(operator, "Operand must be a number.")
  }

  private fun isTruthy(operand: Any?): Boolean {
    return when (operand) {
      is Boolean -> operand
      null -> false
      else -> true
    }
  }

  class RuntimeError(val token: Token, message: String?) : RuntimeException(message)
}
