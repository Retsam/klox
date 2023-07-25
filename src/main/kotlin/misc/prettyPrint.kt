package misc

import Binary
import Expr
import Grouping
import Literal
import Unary

fun prettyPrint(expr: Expr): String {
  return when (expr) {
    is Binary -> parenthesize(expr.operator.lexeme, expr.left, expr.right)
    is Grouping -> parenthesize("group", expr.expression)
    is Literal ->
        if (expr.value == null) {
          "nil"
        } else expr.value.toString()
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
