package misc

import Assign
import Binary
import BlockStmt
import ClassStmt
import ExpressionStmt
import Function
import FunctionCall
import Grouping
import IfStmt
import Literal
import Logical
import PrintStmt
import ReturnStmt
import StmtExpr
import Unary
import VarStmt
import Variable
import WhileStmt

fun prettyPrint(expr: StmtExpr): String {
  return when (expr) {
    is Assign -> parenthesize("=", expr, expr.value)
    is ClassStmt -> parenthesize("class ${expr.name.lexeme}", *expr.methods.toTypedArray())
    is Function ->
        parenthesize(
            "fun ${expr.name.lexeme} ${
              expr.parameters.joinToString(
                  ", ",
                  transform = { it.lexeme },
              )
            }",
            *expr.body.toTypedArray())
    is FunctionCall -> parenthesize("call", expr.primary, *expr.arguments.toTypedArray())
    is Binary -> parenthesize(expr.operator.lexeme, expr.left, expr.right)
    is Grouping -> parenthesize("group", expr.expression)
    is Literal ->
        if (expr.value == null) {
          "nil"
        } else expr.value.toString()
    is Logical -> parenthesize(expr.operator.lexeme, expr.left, expr.right)
    is Variable -> expr.name.lexeme
    is Unary -> parenthesize(expr.operator.lexeme, expr.right)

    // Statements
    is BlockStmt -> parenthesize("block", *expr.statements.toTypedArray())
    is ExpressionStmt -> parenthesize("expression", expr.expr)
    is IfStmt -> parenthesize("if", expr.condition, expr.thenBranch, expr.elseBranch)
    is PrintStmt -> parenthesize("print", expr.expr)
    is ReturnStmt -> parenthesize("return", expr.value)
    is VarStmt -> parenthesize("var ${expr.identifier}", expr.expr)
    is WhileStmt -> parenthesize("while", expr.condition, expr.body)
  }
}

fun parenthesize(name: String, vararg exprs: StmtExpr?): String {
  val builder = StringBuilder()
  builder.append("(").append(name)
  for (expr in exprs.filterNotNull()) {
    builder.append(" ")
    builder.append(prettyPrint(expr))
  }
  builder.append(")")
  return builder.toString()
}
