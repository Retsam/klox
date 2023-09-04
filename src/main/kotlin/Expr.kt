sealed interface Expr

class Assign(val name: Token, val value: Expr) : Expr

class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr

class Grouping(val expression: Expr) : Expr

class Literal(val value: Any?) : Expr

class Unary(val operator: Token, val right: Expr) : Expr

class Variable(val name: Token) : Expr

sealed interface Stmt

class BlockStmt(val statements: List<Stmt>) : Stmt

class ExpressionStmt(val expr: Expr) : Stmt

class VarStmt(val identifier: Token, val expr: Expr) : Stmt

class PrintStmt(val expr: Expr) : Stmt
