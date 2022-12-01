package plc.project;

import java.io.PrintWriter;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Global ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Function ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        print(ast.getExpression(), ';');
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        print(ast.getReceiver(), " = ", ast.getValue(), ';');
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        print("if ", '(' , ast.getCondition(), ')', " {");
        if (!ast.getThenStatements().isEmpty())
        {
            newline(++indent);
            for (int i = 0; i < ast.getThenStatements().size(); i++)
            {
                if (i != 0) newline(indent);
                print(ast.getThenStatements().get(i));
            }
            newline(--indent);
        }
        print('}');

        if (!ast.getElseStatements().isEmpty())
        {
            print(" else ", '{');
            newline(++indent);
            for (int i = 0; i < ast.getElseStatements().size(); i++)
            {
                if (i != 0) newline(indent);
                print(ast.getElseStatements().get(i));
            }
            newline(--indent);
            print('}');
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        print("return ", ast.getValue(), ';');
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        // Need to make sure BigDecimal works
        if (ast.getType().equals(Environment.Type.CHARACTER)) print('\'', ast.getLiteral().toString(), '\'');
        if (ast.getType().equals(Environment.Type.STRING)) print('\"', ast.getLiteral().toString(), '\"');
        if (ast.getType().equals(Environment.Type.INTEGER) || ast.getType().equals(Environment.Type.DECIMAL) || ast.getType().equals(Environment.Type.BOOLEAN)) print(ast.getLiteral().toString());
        if (ast.getType().equals(Environment.Type.NIL)) print("null");

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        // This needs to be tested
        print('(', ast.getExpression(), ')');
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        // Need to check case 2 ^ 3 + 1
        if (ast.getOperator().equals("^")) {
            print("Math.pow(", ast.getLeft(), ", ", ast.getRight(), ')');
            return null;
        }
        print (ast.getLeft(), ' ', ast.getOperator(), ' ', ast.getRight());

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        // Need to add support for list access
        print(ast.getVariable().getJvmName());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        throw new UnsupportedOperationException(); //TODO
    }

}
