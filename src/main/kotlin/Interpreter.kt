class Interpreter {
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
      is ExpressionStmt -> {
        evaluate(stmt.expr)
      }

      is PrintStmt -> {
        val value = evaluate(stmt.expr)
        println(stringify(value))
      }

//      is AssignStmt -> {
//        val value = evaluate(stmt.expr)
//      }
    }
  }

  private fun evaluate(expr: Expr): Any? {
    return when (expr) {
      is Binary -> binaryOperation(evaluate(expr.left), expr.operator, evaluate(expr.right))
      is Grouping -> evaluate(expr.expression)
      is Literal -> expr.value
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
        throw RuntimeError(operator, "Binary operator not supported.")
      }
    }
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
