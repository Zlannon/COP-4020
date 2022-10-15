package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        try {
            List<Ast.Global> globals = new ArrayList<>();
            List<Ast.Function> functions = new ArrayList<>();

            if (tokens.has(0)) {
                if (peek("LIST") || peek("VAR") || peek("VAL")) {
                    globals.add(parseGlobal());
                } else if (match("FUN")) {
                    functions.add(parseFunction());
                }
            }
            return new Ast.Source(globals, functions);
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a global, aka {@code LIST|VAL|VAR}.
     */
    public Ast.Global parseGlobal() throws ParseException {
        try {
            // Need to check end of TokenStream if there is a ;, if not throw a ParseException
            if(match("LIST")) {
                return parseList();
            } else if (match("VAR")) {
                return parseMutable();
            } else if (match("VAL")) {
                return parseImmutable();
            } else {
                throw new ParseException("Expected list, mutable, or immutable", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }
    }

    /**
     * Parses the {@code list} rule. This method should only be called if the
     * next token declares a list, aka {@code LIST}.
     */
    public Ast.Global parseList() throws ParseException {
        try {
            if(match(Token.Type.IDENTIFIER)) {
                String lhs = tokens.get(-1).getLiteral();
                if(!match("=")) {
                    throw new ParseException("Expected '='", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
                if(!match("[")) {
                    throw new ParseException("Expected '['", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
                Ast.Expression initialExpression = parseExpression();
                List<Ast.Expression> exprs = new ArrayList<>();
                exprs.add(initialExpression);

                //add expressions until no more commas
                while (match(",")) {
                    exprs.add(parseExpression());
                }
                Optional<Ast.Expression> list = Optional.of(new Ast.Expression.PlcList(exprs));
                return new Ast.Global(lhs, true, list);
            } else {
                throw new ParseException("Missing Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }
    }

    /**
     * Parses the {@code mutable} rule. This method should only be called if the
     * next token declares a mutable global variable, aka {@code VAR}.
     */
    public Ast.Global parseMutable() throws ParseException {
        try {
            if (match(Token.Type.IDENTIFIER))  {
                //get left hand side
                String lhs = tokens.get(-1).getLiteral();
                //check for equal
                if(!match("=")) {
                    //return statement
                    return new Ast.Global(lhs, true, Optional.empty());
                }

                //get right hand side
                Optional<Ast.Expression> rhs = Optional.of(parseExpression());
                
                //return statement
                return new Ast.Global(lhs, true, rhs);
            } else {
                throw new ParseException("Missing identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }
    }

    /**
     * Parses the {@code immutable} rule. This method should only be called if the
     * next token declares an immutable global variable, aka {@code VAL}.
     */
    public Ast.Global parseImmutable() throws ParseException {
        try {
            if (match(Token.Type.IDENTIFIER)) {
                String lhs = tokens.get(-1).getLiteral();
                if(!match("=")) {
                    //return statement
                    throw new ParseException("Expected '='", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }

                Optional<Ast.Expression> rhs = Optional.of(parseExpression());
                return new Ast.Global(lhs, false, rhs);
            } else {
                throw new ParseException("Missing identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }
    }

    /**
     * Parses the {@code function} rule. This method should only be called if the
     * next tokens start a method, aka {@code FUN}.
     */
    public Ast.Function parseFunction() throws ParseException {
        try {
            if(match(Token.Type.IDENTIFIER)) {
                String funName = tokens.get(-1).getLiteral();

                if(match("(")) {
                    List<String> parameters = new ArrayList<>();

                    while(match(Token.Type.IDENTIFIER)) {
                        parameters.add(tokens.get(-1).getLiteral());
                        if(!match(",")) {
                            if(!peek(")")) {
                                throw new ParseException("Expected comma", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                            }
                        }
                    }
                    if(!match(")")) {
                        throw new ParseException("Expected parenthesis", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    }

                    if(!match("DO")) {
                        throw new ParseException("Expected DO", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    }

                    List<Ast.Statement> statements = parseBlock();
                    if(!tokens.get(-1).getLiteral().equals("END")) {
                        throw new ParseException("Expected END", tokens.get(-1).getIndex());
                    }
                    return new Ast.Function(funName, parameters, statements);
                } else {
                    throw new ParseException("Expected parenthesis", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
            } else {
                throw new ParseException("Expected identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }
    }

    /**
     * Parses the {@code block} rule. This method should only be called if the
     * preceding token indicates the opening a block.
     */
    public List<Ast.Statement> parseBlock() throws ParseException {
        try {
            List<Ast.Statement> statements = new ArrayList<>();
            while (!match("END")) {
                statements.add(parseStatement());
                if(peek("ELSE")) return statements;
            }

            return statements;
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException {
        try {
            //if else tree for statements
            if(match("LET")) {
                return parseDeclarationStatement();
            } else if (match("SWITCH")) {
                return parseSwitchStatement();
            } else if (match("IF")) {
                return parseIfStatement();
            } else if (match("WHILE")) {
                return parseWhileStatement();
            } else if (match("RETURN")) {
                return parseReturnStatement();
            } else {
                //get left hand side
                Ast.Expression lhs = parseExpression();
                //check for equal
                if(!match("=")) {
                    //check for semi colon
                    if(!match(";")) {
                        throw new ParseException("Expected ;", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    }
                    //return statement
                    return new Ast.Statement.Expression(lhs);
                }
                //get right hand side
                Ast.Expression rhs = parseExpression();
                //check for semi colon
                if(!match(";")) {
                    throw new ParseException("Expected ;", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
                //return statement
                return new Ast.Statement.Assignment(lhs, rhs);
            }
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        try {
            if (match(Token.Type.IDENTIFIER)) {
                String lhs = tokens.get(-1).getLiteral();
                Optional<Ast.Expression> rhs = Optional.empty();
                if (match("=")) rhs = Optional.of(parseExpression());

                // check for semi colon
                if (!match(";")) {
                    throw new ParseException("Expected ;", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }

                return new Ast.Statement.Declaration(lhs, rhs);
            } else {
                throw new ParseException("Missing identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        Ast.Expression expression = parseExpression();
        if (!match("DO"))
            throw new ParseException("Missing DO", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());

        List<Ast.Statement> doBlock = parseBlock();
        List<Ast.Statement> elseBlock = new ArrayList<>();
        if (match("ELSE")) elseBlock = parseBlock();

        return new Ast.Statement.If(expression, doBlock, elseBlock);
    }

    /**
     * Parses a switch statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a switch statement, aka
     * {@code SWITCH}.
     */
    public Ast.Statement.Switch parseSwitchStatement() throws ParseException {
        try {
            Ast.Expression expression = parseExpression();
            List<Ast.Statement.Case> cases = new ArrayList<>();
            while(match("CASE")) {
                cases.add(parseCaseStatement());
            }
            if(!match("DEFAULT")) {
                throw new ParseException("Expected 'DEFAULT'", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
            cases.add(new Ast.Statement.Case(Optional.empty(), parseBlock()));
            if(!match("END")) {
                throw new ParseException("Expected 'END'", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
            return new Ast.Statement.Switch(expression, cases);
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }
    }

    /**
     * Parses a case or default statement block from the {@code switch} rule.
     * This method should only be called if the next tokens start the case or
     * default block of a switch statement, aka {@code CASE} or {@code DEFAULT}.
     */
    public Ast.Statement.Case parseCaseStatement() throws ParseException {
        Optional<Ast.Expression> caseExpr = Optional.of(parseExpression());
        if(!match(":")) {
            throw new ParseException("Expected ':'", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }
        return new Ast.Statement.Case(caseExpr, parseBlock());
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        try {
            Ast.Expression expression = parseExpression();
            if (!match("DO"))
                throw new ParseException("Missing DO", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());

            List<Ast.Statement> statements = parseBlock();

            return new Ast.Statement.While(expression, statements);
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());

        }
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        try {
            Ast.Expression expression = parseExpression();
            if (!match(";")) {
                throw new ParseException("Expected ;", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
            return new Ast.Statement.Return(expression);
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }    
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expression parseExpression() throws ParseException {
        try {
            //return logical expression
            return parseLogicalExpression();
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expression parseLogicalExpression() throws ParseException {
        try {
            //return comparison expression
            Ast.Expression fullExpression = parseComparisonExpression();
            //check for logical characters
            while (match("&&") || match("||")) {
                //get operation and right expression
                String operation = tokens.get(-1).getLiteral();
                Ast.Expression rightExpression = parseComparisonExpression();
                //set full expression to binary
                fullExpression = new Ast.Expression.Binary(operation, fullExpression, rightExpression);
            }
            //return expression
            return fullExpression;
        } catch(ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expression parseComparisonExpression() throws ParseException {
        try {
            //return additive expression
            Ast.Expression fullExpression = parseAdditiveExpression();
            //check for comparison characters
            while(match("<") || match(">") || match("==") || match("!=")) {
                //get operation and right expression
                String operation = tokens.get(-1).getLiteral();
                Ast.Expression rightExpression = parseComparisonExpression();
                //set full expression to binary
                fullExpression = new Ast.Expression.Binary(operation, fullExpression, rightExpression);
            }
            return fullExpression;
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expression parseAdditiveExpression() throws ParseException {
        try {
            //return multiplicative expression
            Ast.Expression fullExpression = parseMultiplicativeExpression();
            //check for additive characters
            while(match("+") || match("-")) {
                //get operation and right expression
                String operation = tokens.get(-1).getLiteral();
                Ast.Expression rightExpression = parseMultiplicativeExpression();
                //set full expression to binary
                fullExpression = new Ast.Expression.Binary(operation, fullExpression, rightExpression);
            }
            //return expression
            return fullExpression;
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        try {
            //return primary expression
            Ast.Expression fullExpression = parsePrimaryExpression();
            //check for multiplicative characters
            while(match("/") || match("*") || match("^")) {
                //get operation and left expression
                String operation = tokens.get(-1).getLiteral();
                Ast.Expression rightExpression = parsePrimaryExpression();
                //set full expression to binary operation
                fullExpression = new Ast.Expression.Binary(operation, fullExpression, rightExpression);
            }
            //return expression
            return fullExpression;
        } catch (ParseException p) {
            throw new ParseException(p.getMessage(), p.getIndex());
        }
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expression parsePrimaryExpression() throws ParseException {
        //check for nil
        if(match("NIL")) {
            //return null expression
            return new Ast.Expression.Literal(null);
        //check for true
        } else if (match("TRUE")) {
            //return true expression
            return new Ast.Expression.Literal(true);
        //check for false
        } else if (match("FALSE")) {
            //return false expression
            return new Ast.Expression.Literal(false);
        //check for int token
        } else if (match(Token.Type.INTEGER)) {
            //return BigInteger token
            return new Ast.Expression.Literal(new BigInteger(tokens.get(-1).getLiteral()));
        //check for decimal token
        } else if (match(Token.Type.DECIMAL)) {
            //return BigDecimal token
            return new Ast.Expression.Literal(new BigDecimal(tokens.get(-1).getLiteral()));
        //check for character token
        } else if (match(Token.Type.CHARACTER)) {
            //return character token
            return new Ast.Expression.Literal(tokens.get(-1).getLiteral().charAt(1));
        //check for string token
        } else if (match(Token.Type.STRING)) {
            //grab the literal
            String str = tokens.get(-1).getLiteral();
            str = str.substring(1, str.length() - 1);
            //change the \\ to proper escape characters
            if(str.contains("\\")) {
                str = str.replace("\\n", "\n")
                         .replace("\\t", "\t")
                         .replace("\\b", "\b")
                         .replace("\\r", "\r")
                         .replace("\\'", "'")
                         .replace("\\\\", "\\")
                         .replace("\\\"", "\"");
            }
            //return string expression
            return new Ast.Expression.Literal(str);
        //Check for identifier token
        } else if (match(Token.Type.IDENTIFIER)) {
            //get literal
            String name = tokens.get(-1).getLiteral();
            //check for parenthesis
            if(match("(")) {
                //check for empty list
                if (!match(")")) {
                    //make array list
                    Ast.Expression initialExpression = parseExpression();
                    List<Ast.Expression> args = new ArrayList<>();
                    args.add(initialExpression);

                    //add expressions until no more commas
                    while (match(",")) {
                        args.add(parseExpression());
                    }
                    //Check for ending parenthesis
                    if (match(")")) {
                        return new Ast.Expression.Function(name, args);
                    } else {
                        throw new ParseException("Need closing parenthesis", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    }
                } else {
                    //check for ending parenthesis
                    if (!tokens.get(-1).getLiteral().equals(")")) {
                        throw new ParseException("Need closing parenthesis", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    } else {
                        return new Ast.Expression.Function(name, Collections.emptyList());
                    }
                }
            //check for brace
            } else if (match("[")){
                //check for no expression
                if (!match("]")) {
                    Ast.Expression expression = parseExpression();
                    //check for closing brace
                    if (match("]")) {
                        return new Ast.Expression.Access(Optional.of(expression), name);
                    } else {
                        throw new ParseException("Need closing brace", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    }
                } else {
                    //check for closing brace
                    if (!tokens.get(-1).getLiteral().equals("]")) {
                        throw new ParseException("Need closing brace", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    } else {
                        return new Ast.Expression.Access(Optional.empty(), name);
                    }
                }
            } else {
                //return empty if no braces or parenthesis
                return new Ast.Expression.Access(Optional.empty(), name);
            }
        //check for group expression        
        } else if (match("(")) {
            Ast.Expression expression = parseExpression();
            //check for closing parenthesis
            if(!match(")")) {
                throw new ParseException("Need closing parenthesis", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
            //return group expression
            return new Ast.Expression.Group(expression);
        } else {
            //throw parse exception if no valid primary expressions
            throw new ParseException("Invalid primary expression", tokens.get(0).getIndex());
        }
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!tokens.has(i)) {
                return false;
            } else if (patterns[i] instanceof Token.Type) {
                if (patterns[i] != tokens.get(i).getType()) {
                    return false;
                }
            } else if (patterns[i] instanceof String) {
                if (!patterns[i].equals(tokens.get(i).getLiteral())) {
                    return false;
                }
            } else {
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);
        if(peek) {
            for(int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}