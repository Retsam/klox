class Environment(private val enclosure: Environment? = null) {
  private val values = HashMap<String, Any?>()

  fun declare(name: String, value: Any?): Environment {
    values[name] = value
    return this
  }

  fun assign(token: Token, value: Any?): Environment {
    val name = token.lexeme
    if (!values.containsKey(name))
        throw Interpreter.RuntimeError(token, "Undefined variable '${name}'.")
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
        interpreter.environment.declare(func.parameters[i].lexeme, arguments[i])
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
    environment.declare("this", instance)
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
    val method = clazz.getMethod(key)
    if (method != null) {
      return method.bind(this)
    }
    throw Interpreter.RuntimeError(name, "Undefined property '${key}'.")
  }

  override fun toString(): String {
    return "${clazz.name.lexeme} instance"
  }
}

class LoxClass(
    val name: Token,
    private val superClass: LoxClass?,
    private val methods: HashMap<String, LoxCallableFunction>
) : LoxCallable {

  fun getMethod(name: String): LoxCallableFunction? {
    if (methods.containsKey(name)) {
      return methods[name]
    }
    if (superClass != null) {
      return superClass.getMethod(name)
    }
    return null
  }
  override fun call(interpreter: Interpreter, arguments: List<Any?>): LoxInstance {
    val instance = LoxInstance(this)
    getMethod("init")?.bind(instance)?.call(interpreter, arguments)
    return instance
  }

  override fun arity(): Int {
    val initializer = getMethod("init")
    if (initializer != null) {
      return initializer.arity()
    }
    return 0
  }

  override fun toString(): String {
    return name.lexeme
  }
}

class Interpreter {
  var environment =
      Environment()
          .declare(
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
        val prevEnvironment = environment
        val superClass =
            stmt.superclass?.let {
              val superClass = evaluate(it)
              if (superClass !is LoxClass) {
                throw RuntimeError(stmt.superclass.name, "Superclass must be a class.")
              }
              environment = Environment(environment)
              environment.declare("super", superClass)

              superClass
            }

        val methods = HashMap<String, LoxCallableFunction>()
        for (method in stmt.methods) {
          methods[method.name.lexeme] =
              LoxCallableFunction(method, environment, method.name.lexeme == "init")
        }
        environment = prevEnvironment // Might be a noop if we didn't have a superclass
        environment.declare(stmt.name.lexeme, LoxClass(stmt.name, superClass, methods))
      }
      is ExpressionStmt -> {
        evaluate(stmt.expr)
      }
      is Function -> {
        environment.declare(stmt.name.lexeme, LoxCallableFunction(stmt, environment, false))
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
        environment.declare(stmt.identifier.lexeme, value)
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
          null -> globals.assign(expr.name, value)
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
      is Super -> {
        val distance = locals[expr]
        assert(distance != null && distance > 0) {
          "Assertion failed: invalid distance resolving variable."
        }
        val superClass = environment.getAt(distance!!, "super") as LoxClass
        val instance = environment.getAt(distance - 1, "this") as LoxInstance
        val method =
            superClass.getMethod(expr.method.lexeme)?.bind(instance)
                ?: throw RuntimeError(expr.method, "Undefined property '${expr.method.lexeme}'.")
        method
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
    throw RuntimeError(operator, "Operands must be numbers.")
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
