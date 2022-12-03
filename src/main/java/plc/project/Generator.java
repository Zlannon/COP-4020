package plc.project;

import java.io.PrintWriter;
import java.util.List;

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
        //print 'public class Main {'
        print("public class Main {");
        //newlines
        newline(indent);
        newline(++indent);
        //print all globals
        for(Ast.Global global : ast.getGlobals()) {
            print(global);
            newline(indent);
        }
        //print 'public static void main(String[] args) {'
        print("public static void main(String[] args) {");
        //indented newline
        newline(++indent);
        //print 'System.exit(new Main().main());'
        print("System.exit(new Main().main());");
        //unindented newline
        newline(--indent);
        //print }
        print("}");

        //for all functions
        for(Ast.Function function : ast.getFunctions()) {
            //unindented newline
            newline(--indent);
            //indented newline
            newline(++indent);
            //print function
            print(function);
        }
        //unindented newline
        newline(--indent);
        //newline
        newline(indent);
        //print }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {
        //check if immutable
        if(!ast.getMutable()) {
            //print final modifier
            print("final ");
        }
        //print type
        print(Environment.getType(ast.getTypeName()).getJvmName());
        //check for list
        if (ast.getVariable().getJvmName().equals("list")) {
            //print [] if list
            print("[]");
        }
        //print name
        print(" ", ast.getName());
        //check for value
        if(ast.getValue().isPresent()) {
            //print '= value' if exists
            print(" = ");
            print(ast.getValue().get());
        }
        //print ;
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Function ast) {
        //check for return type name
        if(ast.getReturnTypeName().isPresent()) {
            //print if exists
            print(Environment.getType(ast.getReturnTypeName().get()).getJvmName());
        }
        //print space and name along with (
        print(" ", ast.getName(), "(");
        //check if parameters exist and print them comma separated
        if(ast.getParameters().size() == ast.getParameterTypeNames().size() && ast.getParameters().size() > 0) {
            for(int i = 0; i < ast.getParameters().size(); i++) {
                print(Environment.getType(ast.getParameterTypeNames().get(i)).getJvmName(), " ", ast.getParameters().get(i), ", ");
            }
            print(Environment.getType(ast.getParameterTypeNames().get(ast.getParameterTypeNames().size() - 1)).getJvmName(), " ", ast.getParameters().get(ast.getParameters().size() - 1));
        }
        //print closing ) and opening {
        print(") {");
        //check for statements
        if(!ast.getStatements().isEmpty()) {
            indent++;
            //print all statements
            for(Ast.Statement statement : ast.getStatements()) {
                newline(indent);
                print(statement);
            }
            newline(--indent);
        }
        //closing }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        //print expression with ;
        print(ast.getExpression(), ';');
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        //if typename exists
        if(ast.getTypeName().isPresent()) {
            //print typename
            print(Environment.getType(ast.getTypeName().get()).getJvmName());
        } else {
            //check for value
            if(ast.getValue().isPresent()) {
                //print value if exists
                print(ast.getValue().get().getType().getJvmName());
            } else {
                //else throw runtime exception
                throw new RuntimeException();
            }
        }

        //print space then name
        print(" ", ast.getName());

        //check for value
        if(ast.getValue().isPresent()) {
            //print '= value'
            print(" = ");
            print(ast.getValue().get());
        }

        //print ;
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        //print receiver = value;
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
        //header
        print("switch (", ast.getCondition(), ") {");
        //increment indent
        indent++;
        //print all cases
        for(int i = 0; i < ast.getCases().size() - 1; i++) {
            newline(indent);
            visit(ast.getCases().get(i));
        }
        //check for default
        if(!ast.getCases().isEmpty()) {
            //default header
            newline(indent);
            print("default:");
            indent++;
            //print default statements
            for(Ast.Statement statement : ast.getCases().get(ast.getCases().size() - 1).getStatements()) {
                newline(indent);
                visit(statement);
            }
            //decrement indent
            indent--;
        }
        //print closing }
        newline(--indent);
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        //print case header
        print("case ", ast.getValue().get(), ":");
        //increment indent
        indent++;
        //print all statements
        for(Ast.Statement statement : ast.getStatements()) {
            newline(indent);
            visit(statement);
        }
        //print break;
        newline(indent);
        print("break;");
        //decrement indent
        indent--;
        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        //print while header
        print("while (", ast.getCondition(), ") {");
        //increase indent
        indent++;
        //loop statements
        for(Ast.Statement statement : ast.getStatements()) {
            //newline for each
            newline(indent);
            //print statements
            print(statement);
        }
        //newline subtract indent
        newline(--indent);
        //print closing bracket
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        //print return statement
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
        //print variable jvm name
        print(ast.getVariable().getJvmName());
        //if offset
        if(ast.getOffset().isPresent()) {
            //print brackets with offset in between
            print("[", ast.getOffset().get(), "]");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        //print function name with open parenthesis
        print(ast.getFunction().getJvmName(), "(");
        //check if arguments exist
        if(ast.getArguments().size() > 0) {
            //loop through arguments and print them comma separated
            for(int i = 0; i < ast.getArguments().size() - 1; i++) {
                visit(ast.getArguments().get(i));
                print(",");
            }
            //print last argument
            print(ast.getArguments().get(ast.getArguments().size() - 1));
        }
        //print closing parenthesis
        print(")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        //print bracket
        print("{");
        //loop through values and print them comma separated
        for(int i = 0; i < ast.getValues().size() - 1; i++) {
            visit(ast.getValues().get(i));
            print(", ");
        }
        //print last value
        print(ast.getValues().get(ast.getValues().size() - 1));
        //print closing bracket
        print("}");
        return null;
    }

}
