package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        //visit all globals
        for(Ast.Global global : ast.getGlobals()) {
            visit(global);
        }
        //visit all functions
        for(Ast.Function function : ast.getFunctions()) {
            visit(function);
        }
        //return main/0
        return scope.lookupFunction("main", 0).invoke(Collections.emptyList());
    }

    @Override
    public Environment.PlcObject visit(Ast.Global ast) {
        Environment.PlcObject val = Environment.NIL;
        //check for value
        if(ast.getValue().isPresent()) {
            //get val
            val = visit(ast.getValue().get());
        }
        //define var with nil/val
        scope.defineVariable(ast.getName(),ast.getMutable(),val);
        //return nil
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Function ast) {
        //define function
        scope.defineFunction(ast.getName(), ast.getParameters().size(), args-> {
            try {
                scope = new Scope(scope);
                //define vars
                for(int i = 0; i < args.size(); i++) {
                    scope.defineVariable(ast.getParameters().get(i), true, args.get(i));
                }
                //visit statements
                for(Ast.Statement statement : ast.getStatements()) {
                    visit(statement);
                }
            } catch (Return r) {
                //return
                return r.value;
            } finally {
                //restore scope
                scope = scope.getParent();
            }
            //return nil
            return Environment.NIL;
        });
        //return nil
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        Optional<Ast.Expression> optional = ast.getValue();
        Boolean present = optional.isPresent();
        if(present) {
            Ast.Expression expr = (Ast.Expression) optional.get();
            scope.defineVariable(ast.getName(), true, visit(expr));
        } else {
            scope.defineVariable(ast.getName(), true, Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        Ast.Expression.Access accessExpression = (Ast.Expression.Access) ast.getReceiver();
        if(accessExpression instanceof Ast.Expression.Access) {
            if((accessExpression).getOffset().isPresent()) {
                //TODO: list access
            } else {
                scope.lookupVariable((accessExpression).getName()).setValue(visit(ast.getValue()));
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        //check for boolean condition
        if(requireType(Boolean.class, visit(ast.getCondition())) != null) {
            try {
                //create new scope
                scope = new Scope(scope);
                //if true visit then statements
                if((Boolean) visit(ast.getCondition()).getValue()) {
                    ast.getThenStatements().forEach(this::visit);
                } else {
                    //if false visit else statements
                    ast.getElseStatements().forEach(this::visit);
                }
            } finally {
                //restore scope
                scope = scope.getParent();
            }
        }
        //return nil
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Switch ast) {
        //TODO
        try {
            scope = new Scope(scope);
        } finally {
            scope = scope.getParent();
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Case ast) {
        return Environment.NIL; //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        while(requireType(Boolean.class, visit(ast.getCondition()))) {
            try {
                scope = new Scope(scope);
                ast.getStatements().forEach(this::visit);
            } finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {
        throw new Return(visit(ast.getValue()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        if (ast.getLiteral() == null) {
            return Environment.NIL;
        }
        return Environment.create((ast.getLiteral()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        //get left side of binary
        Environment.PlcObject left = visit(ast.getLeft());
        //check for +
        if(ast.getOperator().equals("+")) {
            //check for bigint
            if (left.getValue() instanceof BigInteger) {
                if (visit(ast.getRight()).getValue() instanceof BigInteger) {
                    //return addition
                    return Environment.create(requireType(BigInteger.class, left).add(requireType(BigInteger.class, visit(ast.getRight()))));
                }
                throw new RuntimeException();
            }
            //check for bigdecimal
            if(left.getValue() instanceof BigDecimal) {
                if(visit(ast.getRight()).getValue() instanceof BigDecimal) {
                    //return addition
                    return Environment.create(requireType(BigDecimal.class, left).add(requireType(BigDecimal.class, visit(ast.getRight()))));
                }
                throw new RuntimeException();
            }
            //check for string
            if(left.getValue() instanceof String) {
                if(visit(ast.getRight()).getValue() instanceof String) {
                    //return concatenation
                    return Environment.create(requireType(String.class, left) + requireType(String.class, visit(ast.getRight())));
                }
                throw new RuntimeException();
            }
        //check for -
        } else if (ast.getOperator().equals("-")) {
            //check for bigint
            if (left.getValue() instanceof BigInteger) {
                if (visit(ast.getRight()).getValue() instanceof BigInteger) {
                    //return subtraction
                    return Environment.create(requireType(BigInteger.class, left).subtract(requireType(BigInteger.class, visit(ast.getRight()))));
                }
                throw new RuntimeException();
            }
            //check for bigdecimal
            if(left.getValue() instanceof BigDecimal) {
                if(visit(ast.getRight()).getValue() instanceof BigDecimal) {
                    //return subtraction
                    return Environment.create(requireType(BigDecimal.class, left).subtract(requireType(BigDecimal.class, visit(ast.getRight()))));
                }
                throw new RuntimeException();
            }
        //check for *
        } else if (ast.getOperator().equals("*")) {
            //check for bigint
            if (left.getValue() instanceof BigInteger) {
                if (visit(ast.getRight()).getValue() instanceof BigInteger) {
                    //return multiplication
                    return Environment.create(requireType(BigInteger.class, left).multiply(requireType(BigInteger.class, visit(ast.getRight()))));
                }
                throw new RuntimeException();
            }
            //check for bigdecimal
            if(left.getValue() instanceof BigDecimal) {
                if(visit(ast.getRight()).getValue() instanceof BigDecimal) {
                    //return multiplication
                    return Environment.create(requireType(BigDecimal.class, left).multiply(requireType(BigDecimal.class, visit(ast.getRight()))));
                }
                throw new RuntimeException();
            }
        //check for /
        } else if (ast.getOperator().equals("/")) {
            //check for bigint
            if (left.getValue() instanceof BigInteger) {
                if (visit(ast.getRight()).getValue() instanceof BigInteger) {
                    //check for division by 0 error
                    if (((BigInteger) visit(ast.getRight()).getValue()).intValue() == 0) {
                        throw new RuntimeException();
                    }
                    //return division
                    return Environment.create(requireType(BigInteger.class, left).divide(requireType(BigInteger.class, visit(ast.getRight()))));
                }
                throw new RuntimeException();
            }
            //check for bigdecimal
            if (left.getValue() instanceof BigDecimal) {
                if (visit(ast.getRight()).getValue() instanceof BigDecimal) {
                    //check for division by 0 error
                    if (((BigDecimal) visit(ast.getRight()).getValue()).doubleValue() == 0) {
                        throw new RuntimeException();
                    }
                    //return division with half_even rounding
                    return Environment.create(requireType(BigDecimal.class, left).divide(requireType(BigDecimal.class, visit(ast.getRight())),RoundingMode.HALF_EVEN));
                }
                throw new RuntimeException();
            }
        //check for ^
        } else if (ast.getOperator().equals("^")) {
            //check for bigint
            if (left.getValue() instanceof BigInteger) {
                if (visit(ast.getRight()).getValue() instanceof BigInteger) {
                    //exponential calculation
                    BigInteger val = BigInteger.ONE;
                    BigInteger base = requireType(BigInteger.class, left);
                    BigInteger exp = requireType(BigInteger.class, visit(ast.getRight()));
                    while(exp.signum() > 0) {
                        if(exp.testBit(0)) val = val.multiply(base);
                        base = base.multiply(base);
                        exp = exp.shiftRight(1);
                    }
                    //return exponential calculation
                    return Environment.create(val);
                }
                throw new RuntimeException();
            }
        //check for &&
        } else if(ast.getOperator().equals("&&")) {
            //check if left side is false
            if(left.getValue() instanceof Boolean && !(Boolean)left.getValue()) {
                //return false
                return Environment.create(false);
            }
            //check if right side is false
            if(visit(ast.getRight()).getValue() instanceof Boolean && !(Boolean)visit(ast.getRight()).getValue()) {
                //return false
                return Environment.create(false);
            }
            //return true
            if(left.getValue() instanceof Boolean) {
                if(visit(ast.getRight()).getValue() instanceof Boolean) {
                    return Environment.create(true);
                }
                throw new RuntimeException();
            }
        //check for ||
        } else if(ast.getOperator().equals("||")) {
            //check if left side is true
            if (left.getValue() instanceof Boolean && (Boolean) left.getValue()) {
                //return true
                return Environment.create(true);
            }
            //check if right side is true
            if (visit(ast.getRight()).getValue() instanceof Boolean && (Boolean) visit(ast.getRight()).getValue()) {
                //return true
                return Environment.create(true);
            }
            //return false
            if (left.getValue() instanceof Boolean) {
                if (visit(ast.getRight()).getValue() instanceof Boolean) {
                    return Environment.create(false);
                }
                throw new RuntimeException();
            }
        //check for ==
        } else if (ast.getOperator().equals("==")) {
            //return equals value
            return Environment.create(Objects.equals(left.getValue(), visit(ast.getRight()).getValue()));
        //check for !=
        } else if (ast.getOperator().equals("!=")) {
            //return not equals value
            return Environment.create(!Objects.equals(left.getValue(), visit(ast.getRight()).getValue()));
        //check for <
        } else if (ast.getOperator().equals("<")) {
            //check for comparable
            if(left.getValue() instanceof Comparable) {
                if(requireType(left.getValue().getClass(), visit(ast.getRight())) != null) {
                    //return less than value
                    return Environment.create(((Comparable) left.getValue()).compareTo(visit(ast.getRight()).getValue()) < 0);
                }
            }
        //check for >
        } else if (ast.getOperator().equals(">")) {
            //check for comparable
            if(left.getValue() instanceof Comparable) {
                if(requireType(left.getValue().getClass(), visit(ast.getRight())) != null) {
                    //return greater than value
                    return Environment.create(((Comparable) left.getValue()).compareTo(visit(ast.getRight()).getValue()) > 0);
                }
            }
        }
        //return nil
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {
        if(ast.getOffset().isPresent())
            return Environment.NIL; //TODO: List implementation
        return scope.lookupVariable(ast.getName()).getValue();
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        List<Environment.PlcObject> args = new ArrayList<>();
        for(Ast.Expression arg : ast.getArguments()) {
            args.add(visit(arg));
        }
        Environment.Function fun = scope.lookupFunction(ast.getName(), ast.getArguments().size());
        return fun.invoke(args);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.PlcList ast) {
        return Environment.NIL;
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
