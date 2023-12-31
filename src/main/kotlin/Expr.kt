// Shared root to allow functions to accept either a Stmt or an Expr (mostly used for pretty print)
sealed interface StmtExpr

sealed interface Expr : StmtExpr

class Assign(val name: Token, val value: Expr) : Expr

class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr

class FunctionCall(val primary: Expr, val paren: Token, val arguments: List<Expr>) : Expr

class Get(val primary: Expr, val name: Token) : Expr

class Grouping(val expression: Expr) : Expr

class Literal(val value: Any?) : Expr

class Logical(val left: Expr, val operator: Token, val right: Expr) : Expr

class Super(val keyword: Token, val method: Token) : Expr

class This(val keyword: Token) : Expr

class SetExpr(val primary: Expr, val name: Token, val value: Expr) : Expr

class Unary(val operator: Token, val right: Expr) : Expr

class Variable(val name: Token) : Expr

sealed interface Stmt : StmtExpr

class BlockStmt(val statements: List<Stmt>) : Stmt

class ClassStmt(val name: Token, val superclass: Variable?, val methods: List<Function>) : Stmt

class Function(val name: Token, val parameters: List<Token>, val body: List<Stmt>) : Stmt

class IfStmt(val condition: Expr, val thenBranch: Stmt, val elseBranch: Stmt?) : Stmt

class WhileStmt(val condition: Expr, val body: Stmt) : Stmt

class ExpressionStmt(val expr: Expr) : Stmt

class ReturnStmt(val keyword: Token, val value: Expr?) : Stmt

class VarStmt(val identifier: Token, val expr: Expr) : Stmt

class PrintStmt(val expr: Expr) : Stmt
