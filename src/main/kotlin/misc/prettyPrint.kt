package misc

import Assign
import Binary
import Expr
import Grouping
import Literal
import Logical
import Unary
import Variable

fun prettyPrint(expr: Expr): String {
  return when (expr) {
    is Assign -> parenthesize("=", expr, expr.value)
    is Binary -> parenthesize(expr.operator.lexeme, expr.left, expr.right)
    is Grouping -> parenthesize("group", expr.expression)
    is Literal ->
        if (expr.value == null) {
          "nil"
        } else expr.value.toString()
    is Logical -> parenthesize(expr.operator.lexeme, expr.left, expr.right)
    is Variable -> expr.name.lexeme
    is Unary -> parenthesize(expr.operator.lexeme, expr.right)
  }
}

fun parenthesize(name: String, vararg exprs: Expr): String {
  val builder = StringBuilder()
  builder.append("(").append(name)
  for (expr in exprs) {
    builder.append(" ")
    builder.append(prettyPrint(expr))
  }
  builder.append(")")
  return builder.toString()
}
