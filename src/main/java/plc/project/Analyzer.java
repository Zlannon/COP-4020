package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Function function;
    private Environment.Type returnType;
    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        //ensure function is not null and of type integer
        if(scope.lookupFunction("main", 0) != null && scope.lookupFunction("main", 0).getReturnType() == Environment.Type.INTEGER) {
            //visit all globals
            for(Ast.Global global : ast.getGlobals()) {
                visit(global);
            }
            //visit all functions
            for(Ast.Function function : ast.getFunctions()) {
                visit(function);
            }
        } else {
            //throw runtime exception if function is null or not of type integer
            throw new RuntimeException();
        }
        //return null
        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {
        //check for value present
        if (ast.getValue().isPresent()) {
            //get val and type
            Ast.Expression val = ast.getValue().get();
            Environment.Type type = Environment.getType(ast.getTypeName());

            //check for all types in Environment
            if(val.getType() == type
                    || type == Environment.Type.ANY
                    || (type == Environment.Type.COMPARABLE && (val.getType() == Environment.Type.INTEGER
                    || val.getType() == Environment.Type.DECIMAL
                    || val.getType() == Environment.Type.STRING
                    || val.getType() == Environment.Type.CHARACTER)))
            {
                //visit value
                visit(val);
            } else {
                //else throw runtime exception
                throw new RuntimeException();
            }
        }
        //set variable
        ast.setVariable(scope.defineVariable(ast.getName(),ast.getName(),Environment.getType(ast.getTypeName()),ast.getMutable(),Environment.NIL));
        //return null
        return null;
    }

    @Override
    public Void visit(Ast.Function ast) {
        //param type list
        List<Environment.Type> paramTypes = new ArrayList<>();
        //add all parameters to list
        ast.getParameterTypeNames().forEach(name -> {
            paramTypes.add(Environment.getType(name));
        });

        //set return type to nil
        Environment.Type returnType = Environment.Type.NIL;
        //check if return type name is present
        if(ast.getReturnTypeName().isPresent()) {
            //set return type
            returnType = Environment.getType(ast.getReturnTypeName().get());
        }



        //set function
        ast.setFunction(scope.defineFunction(ast.getName(), ast.getName(), paramTypes, returnType, args -> Environment.NIL));
        try {
            //new scope
            scope = new Scope(scope);
            //define all parameters
            for(int i = 0; i < ast.getParameters().size(); i++) {
                scope.defineVariable(ast.getParameters().get(i), ast.getParameters().get(i), paramTypes.get(i), true, Environment.NIL);
            }

            this.returnType = returnType;
            //visit all statements
            for(Ast.Statement statement : ast.getStatements()) {
                visit(statement);
            }
        } finally {
            scope = scope.getParent();
        }
        //return null
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        //check if not ast.expression.function
        if(!(ast.getExpression() instanceof Ast.Expression.Function)) {
            throw new RuntimeException("Expected Ast.Expression.Function");
        }
        //visit expression
        visit(ast.getExpression());
        //return null
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        //check if type and value is present
        if(!ast.getTypeName().isPresent() && !ast.getValue().isPresent()) {
            throw new RuntimeException("Expected type/value during declaration");
        }
        //set type to null
        Environment.Type type = null;
        //check for type and set type if present
        if(ast.getTypeName().isPresent()) {
            type = Environment.getType(ast.getTypeName().get());
        }
        //check if value is present
        if(ast.getValue().isPresent()) {
            //visit value
            visit(ast.getValue().get());
            //if type is null use value type
            if (type == null) {
                type = ast.getValue().get().getType();
            }
            //require assignable type and value
            requireAssignable(type, ast.getValue().get().getType());
        }
        //set variable
        ast.setVariable(scope.defineVariable(ast.getName(),ast.getName(), type, true, Environment.NIL));
        //return null
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        //check if receiver is ast.expression.access
        if(!(ast.getReceiver() instanceof Ast.Expression.Access)) {
            throw new RuntimeException("Expected Ast.Expression.Access");
        }

        //visit receiver and value
        visit(ast.getReceiver());
        visit(ast.getValue());

        requireAssignable(ast.getReceiver().getType(),ast.getValue().getType());
        //return null
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        //visit condition
        visit(ast.getCondition());
        //check if condition is boolean and then statements exist
        if (ast.getCondition().getType() != Environment.Type.BOOLEAN || ast.getThenStatements().isEmpty()) {
            throw new RuntimeException();
        }

        //visit then statements
        for(Ast.Statement then : ast.getThenStatements()) {
            try {
                scope = new Scope(scope);
                visit(then);
            } finally {
                scope = scope.getParent();
            }
        }
        //visit else statements in new scope
        for(Ast.Statement elses : ast.getElseStatements()) {
            try {
                scope = new Scope(scope);
                visit(elses);
            } finally {
                scope = scope.getParent();
            }
        }
        //return null
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        //visit condition
        visit(ast.getCondition());
        for(int i = 0; i < ast.getCases().size(); i++) {
            //get case
            Ast.Statement.Case cases = ast.getCases().get(i);
            //if not DEFAULT
            if(i != ast.getCases().size()-1) {
                //visit value
                visit(cases.getValue().get());
                //check if value type is equal to condition type
                if(cases.getValue().get().getType() != ast.getCondition().getType()) {
                    throw new RuntimeException();
                }
            } else {
                //ensure no value for default
                if(cases.getValue().isPresent()) {
                    throw new RuntimeException();
                }
            }
            try {
                //visit cases in new scope
                scope = new Scope(scope);
                visit(cases);
            } finally {
                scope = scope.getParent();
            }
        }
        //return null
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        //visit all statements
        for (Ast.Statement statement : ast.getStatements()) {
            visit(statement);
        }
        //return null
        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        //visit condition
        visit(ast.getCondition());
        //ensure condition is type boolean
        if(ast.getCondition().getType() != Environment.Type.BOOLEAN) {
            //visit statements in new scope
            try {
                scope = new Scope(scope);
                for (Ast.Statement statement : ast.getStatements()) {
                    visit(statement);
                }
            } finally {
                scope = scope.getParent();
            }
        }
        //return null
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        //visit value
        visit(ast.getValue());
        //check for mismatch
        if(ast.getValue().getType() != returnType) {
            throw new RuntimeException();
        }
        //return null
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        //get literal
        Object literal = ast.getLiteral();
        //check for all types and set ast to specified type
        if(literal == null) {
            ast.setType(Environment.Type.NIL);
        }
        if(literal instanceof Boolean) {
            ast.setType(Environment.Type.BOOLEAN);
        }
        if(literal instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
        }
        if(literal instanceof String) {
            ast.setType(Environment.Type.STRING);
        }
        if(literal instanceof BigInteger) {
            //ensure value is within integer limits
            BigInteger val = (BigInteger) literal;
            if (val.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0
                    && val.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0) {
                ast.setType(Environment.Type.INTEGER);
            } else {
                throw new RuntimeException("Value of int not in range of integer");
            }
        }
        if(literal instanceof BigDecimal) {
            //ensure value is within double limits
            Double val = ((BigDecimal) literal).doubleValue();
            if (val == Double.POSITIVE_INFINITY || val == Double.NEGATIVE_INFINITY) {
                throw new RuntimeException("Value of decimal not in range of double");
            } else {
                ast.setType(Environment.Type.DECIMAL);
            }
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        //make sure expression is of type binary
        if (ast.getExpression() instanceof Ast.Expression.Binary) {
            //visit expression
            visit(ast.getExpression());
            //set type to expression
            ast.setType(ast.getExpression().getType());
            //return null
            return null;
        }
        throw new RuntimeException("Expected Ast.Expression.Binary");
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        //get left and right
        Ast.Expression left = ast.getLeft();
        Ast.Expression right = ast.getRight();
        //visit left and right
        visit(left);
        visit(right);

        //get operator
        String op = ast.getOperator();
        //check for && or ||
        if (op.equals("&&") || op.equals("||")) {
            //ensure left and right are boolean
            if (left.getType() == Environment.Type.BOOLEAN && right.getType() == Environment.Type.BOOLEAN) {
                //set type to boolean
                ast.setType(Environment.Type.BOOLEAN);
                //return null
                return null;
            }
            throw new RuntimeException("boolean type expected");
        }
        //check for <, >, ==, or !=
        if(op.equals("<") || op.equals(">") || op.equals("==") || op.equals("!=")) {
            //ensure type comparable
            requireAssignable(Environment.Type.COMPARABLE, left.getType());
            requireAssignable(Environment.Type.COMPARABLE, right.getType());
            requireAssignable(left.getType(), right.getType());
            //set to boolean
            ast.setType(Environment.Type.BOOLEAN);
        }
        //check for +
        if(op.equals("+")) {
            //check for string
            if(left.getType() == Environment.Type.STRING || right.getType() == Environment.Type.STRING) {
                //set to string
                ast.setType(Environment.Type.STRING);
                return null;
            }
            //check for int or decimal
            if(left.getType() == Environment.Type.INTEGER || left.getType() == Environment.Type.DECIMAL) {
                if(left.getType() == right.getType()) {
                    //set to left type
                    ast.setType(left.getType());
                    return null;
                }
            }
            throw new RuntimeException("Expected Integer or Decimal");
        }
        //check for -, *, /
        if(op.equals("-") || op.equals("*") || op.equals("/")) {
            //ensure int or decimal
            if(left.getType() == Environment.Type.INTEGER || left.getType() == Environment.Type.DECIMAL) {
                if(left.getType() == right.getType()) {
                    //set to left type
                    ast.setType(left.getType());
                    return null;
                }
            }
            throw new RuntimeException("Expected Integer or Decimal");
        }
        //check for ^
        if(op.equals("^")) {
            //ensure integer
            if(left.getType() == Environment.Type.INTEGER) {
                if(left.getType() == right.getType()) {
                    //set to left type
                    ast.setType(Environment.Type.INTEGER);
                    return null;
                }
            }
            throw new RuntimeException("Expected Integer");
        }
        //return null
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        //check for offset
        if(ast.getOffset().isPresent()) {
            //get offset
            Ast.Expression expression = ast.getOffset().get();
            //visit expression
            visit(expression);
            //set variable
            ast.setVariable(expression.getType().getGlobal(ast.getName()));
        } else {
            //set variable
            ast.setVariable(scope.lookupVariable(ast.getName()));
        }
        //return null
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        //get function
        Environment.Function func = scope.lookupFunction(ast.getName(), ast.getArguments().size());

        //get args and arg types
        List<Ast.Expression> args = ast.getArguments();
        List<Environment.Type> argTypes = func.getParameterTypes();

        //visit all args
        for(int i = 0; i < args.size(); i++) {
            visit(args.get(i));
            requireAssignable(argTypes.get(i), args.get(i).getType());
        }
        //set function
        ast.setFunction(func);
        //return null
        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        return null; //TODO
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        //if same var return
        if (target.getName().equals(type.getName()))
            return;

        //if any return
        if(target.getName().equals("Any"))
            return;
        //check for all types
        if(target.getName().equals("Comparable")) {
            if (type.getName().equals("Integer")
                    || type.getName().equals("Decimal")
                    || type.getName().equals("Character")
                    || type.getName().equals("String")) {
                return;
            }
        }
        //throw if wrong type
        throw new RuntimeException("Incorrect Type");
    }
}
