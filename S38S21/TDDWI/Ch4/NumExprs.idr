module S38S21.TDDWI.Ch4.NumExprs

public export
data Expr val
	= Literal val
	| Add (Expr val) (Expr val)
	| Subtract (Expr val) (Expr val)
	| Multiply (Expr val) (Expr val)

evaluate : (Num val, Neg val) => Expr val -> val
evaluate (Literal v) = v
evaluate (Add a b) = evaluate a + evaluate b
evaluate (Subtract a b) = evaluate a - evaluate b
evaluate (Multiply a b) = evaluate a * evaluate b
