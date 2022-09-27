package plc.project;

import java.util.ArrayList;
import java.util.List;

/**
 * The lexer works through three main functions:
 *
 *  - {@link #lex()}, which repeatedly calls lexToken() and skips whitespace
 *  - {@link #lexToken()}, which lexes the next token
 *  - {@link CharStream}, which manages the state of the lexer and literals
 *
 * If the lexer fails to parse something (such as an unterminated string) you
 * should throw a {@link ParseException} with an index at the character which is
 * invalid.
 *
 * The {@link #peek(String...)} and {@link #match(String...)} functions are * helpers you need to use, they will make the implementation a lot easier. */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    /**
     * Repeatedly lexes the input using {@link #lexToken()}, also skipping over
     * whitespace where appropriate.
     */
    public List<Token> lex() {
        //array for all tokens
        List<Token> tokens = new ArrayList<>();
        //while the charstream still has characters
        while(chars.has(0)) {
            //check for white space
            if(!match(" ") && !match("\b") && !match("\n") && !match("\r") && !match("\t"))
                //lex tokens
                tokens.add(lexToken());
            else {
                //skip whitespace
                chars.skip();
            }
        }
        //return list of tokens
        return tokens;
    }

    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     *
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() {
        //check for identifier regex
        if(peek("(@|[A-Za-z])"))
            return lexIdentifier();
        //check for number regex
        else if(peek("(-|[0-9])"))
            return lexNumber();
        //check for char regex
        else if(peek("\'"))
            return lexCharacter();
        //check for string regex
        else if(peek("\""))
            return lexString();
        //check for operator regex
        else if(peek("(.)"))
            return lexOperator();
        //If program cant parse throw exception
        throw new ParseException("Invalid char", chars.index);
    }

    public Token lexIdentifier() {
        //match regex since 1st index is known
        match("(@|[A-Za-z])");
        //move through charstream
        while(match("[A-Za-z0-9_-]"));
        //return matched characters
        return chars.emit(Token.Type.IDENTIFIER);
    }

    public Token lexNumber() {
        //skip dash
        match("[-]");
        //check leading 0
        if(peek("0")) {
            if(!peek("[0]","[.]"))
                throw new ParseException("Leading 0", chars.index);
        }
        //index charstream until no number
        while(match("[0-9]"));
        //check for decimal
        if(match("[.]")) {
            //check for numbers after decimal
            if(peek("[0-9]+")) {
                //index until no number found
                while (match("[0-9]"));
                //return the token of type decimal
                return chars.emit(Token.Type.DECIMAL);
            } else {
                //throw exception if ends in decimal
                throw new ParseException("Cannot end in decimal", chars.index);
            }
        } else {
            //return integer type if no decimal
            return chars.emit(Token.Type.INTEGER);
        }
    }

    public Token lexCharacter() {
        //first index known
        match("\'");
        //create regex
        String accepted = "[^\'\\n\\r]";
        //check for escape or valid character
        if(peek("\\\\") || peek(accepted)) {
            //if backslash use lexEscape
            if (peek("\\\\"))
                lexEscape();
            else
                match(accepted);
            //if closing ' emit token
            if (match("\'"))
                return chars.emit(Token.Type.CHARACTER);
            else
                //throw error if no closing '
                throw new ParseException("Expected closing quote", chars.index);
        }
        throw new ParseException("Invalid char", chars.index);
    }

    public Token lexString() {
        //create regex
        String accepted = "[^\\\\\"\\n\\r]";
        match("\"");
        //continue until closing "
        while(!peek("\"")) {
            //check for escape
            if(peek("\\\\"))
                lexEscape();
            //check if valid string
            else if(peek(accepted))
                match(accepted);
            else
                //throw error if not valid string
                throw new ParseException("Invalid string", chars.index);
        }
        //close string
        match("\"");
        //emit token
        return chars.emit(Token.Type.STRING);
    }

    public void lexEscape() {
        if(!match("\\\\"))
            throw new ParseException("Invalid escape", chars.index);
        if(!match("[bnrt\'\"\\\\]"))
            throw new ParseException("Invalid character", chars.index);
    }

    public Token lexOperator() {
        //check for special cases
        if(match("!", "=") || match("=","=") || match("&","&") || match("|", "|"))
            //emit token
            return chars.emit(Token.Type.OPERATOR);
        //any character regex except no whitespace
        else if (match("(.)"))
            //emit token
            return chars.emit(Token.Type.OPERATOR);
        throw new ParseException("invalid", chars.index);
    }

    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    public boolean peek(String... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if(!chars.has(i) || !String.valueOf(chars.get(i)).matches(patterns[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */
    public boolean match(String... patterns) {
        boolean peek = peek(patterns);
        if(peek) {
            for(int i = 0; i < patterns.length; i++) {
                chars.advance();
            }
        }
        return peek;
    }

    /**
     * A helper class maintaining the input string, current index of the char
     * stream, and the current length of the token being matched.
     *
     * You should rely on peek/match for state management in nearly all cases.
     * The only field you need to access is {@link #index} for any {@link
     * ParseException} which is thrown.
     */
    public static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        public char get(int offset) {
            return input.charAt(index + offset);
        }

        public void advance() {
            index++;
            length++;
        }

        public void skip() {
            length = 0;
        }

        public Token emit(Token.Type type) {
            int start = index - length;
            skip();
            return new Token(type, input.substring(start, index), start);
        }

    }

}
