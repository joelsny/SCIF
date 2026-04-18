package parser;

import java_cup.runtime.*;
import java.util.Stack;
import java.util.HashMap;
import parser.sym;
import static parser.sym.*;

%%

%class Lexer
%implements sym
%unicode
%cup
%line
%column

%{
    StringBuffer sb = new StringBuffer();
    public static HashMap<String, Integer> keywords = new HashMap<>();
    public static HashMap<Integer,String> tokenNames = new HashMap<>();
    int inbrace;

    Symbol op(int tokenId) {
        return new Symbol(tokenId, yyline + 1, yycolumn + 1);
    }

    Symbol op(int tokenId, Object value) {
        return new Symbol(tokenId, yyline + 1, yycolumn + 1, value);
    }

    Symbol id() {
        return new Symbol(sym.NAME, yyline + 1, yycolumn + 1, new String(yytext()));
    }
   
    Symbol key(int symbolId) {
        return new Symbol(keywords.get(yytext()), yyline + 1, yycolumn + 1, yytext());
    }

    void addKeyword(String name, int value) {
        keywords.put(name, value);
        tokenNames.put(value, name);
    }
    void addOperator(String name, int value) {
        tokenNames.put(value, name);
    }

    protected void init_keywords() {
        addKeyword("false",   Integer.valueOf(sym.FALSE));
        addKeyword("none",    Integer.valueOf(sym.NONE));
        addKeyword("true",    Integer.valueOf(sym.TRUE));
        addKeyword("and",     Integer.valueOf(sym.AND));
//        addKeyword("as",      Integer.valueOf(sym.AS));
        addKeyword("assert",  Integer.valueOf(sym.ASSERT));
        addKeyword("delete",  Integer.valueOf(sym.DELETE));
        addKeyword("break",   Integer.valueOf(sym.BREAK));
        // addKeyword("class",   Integer.valueOf(sym.CLASS));
        addKeyword("continue",Integer.valueOf(sym.CONTINUE));
        addKeyword("else",    Integer.valueOf(sym.ELSE));
        // addKeyword("finally", Integer.valueOf(sym.FINALLY));
        addKeyword("for",     Integer.valueOf(sym.FOR));
        // addKeyword("from",    Integer.valueOf(sym.FROM));
        addKeyword("if",      Integer.valueOf(sym.IF));
        addKeyword("when",      Integer.valueOf(sym.WHEN));
        addKeyword("import",  Integer.valueOf(sym.IMPORT));
        addKeyword("in",      Integer.valueOf(sym.IN));
        addKeyword("is",      Integer.valueOf(sym.IS));
        addKeyword("not",     Integer.valueOf(sym.NOT));
        addKeyword("or",      Integer.valueOf(sym.OR));
        addKeyword("return",  Integer.valueOf(sym.RETURN));
        addKeyword("try",     Integer.valueOf(sym.TRY));
        addKeyword("atomic",     Integer.valueOf(sym.ATOMIC));
        addKeyword("while",   Integer.valueOf(sym.WHILE));
        // addKeyword("with",    Integer.valueOf(sym.WITH));
        addKeyword("endorse", Integer.valueOf(sym.ENDORSE));
        addKeyword("map", Integer.valueOf(sym.MAP));
        addKeyword("contract", Integer.valueOf(sym.CONTRACT));
        addKeyword("interface", Integer.valueOf(sym.INTERFACE));
        addKeyword("struct", Integer.valueOf(sym.STRUCT));
        addKeyword("lock", Integer.valueOf(sym.GUARD));
        addKeyword("extends", Integer.valueOf(sym.EXTENDS));
        addKeyword("implements", Integer.valueOf(sym.IMPLEMENTS));
        //addKeyword("super", Integer.valueOf(sym.SUPER));
        // addKeyword("lock", Integer.valueOf(sym.LOCK));
        addKeyword("else", Integer.valueOf(sym.ELSE));
        addKeyword("new", Integer.valueOf(sym.NEW));
        addKeyword("final", Integer.valueOf(sym.FINAL));
//        addKeyword("static", Integer.valueOf(sym.STATIC));
        addKeyword("throws", Integer.valueOf(sym.THROWS));
        addKeyword("throw", Integer.valueOf(sym.THROW));
        addKeyword("revert", Integer.valueOf(sym.REVERT));
//        addKeyword("endorseIf", Integer.valueOf(sym.ENDORSEIF));
        addKeyword("catch", Integer.valueOf(sym.CATCH));
        addKeyword("rescue", Integer.valueOf(sym.RESCUE));
        addKeyword("exception", Integer.valueOf(sym.EXCEPTION));
        addKeyword("constructor", Integer.valueOf(sym.CONSTRUCTOR));
        addKeyword("assume", Integer.valueOf(sym.ASSUME));
        addKeyword("unchecked", Integer.valueOf(sym.UNCHECKED));
        addKeyword("transient", Integer.valueOf(sym.TRANSIENT));
        addKeyword("event", Integer.valueOf(sym.EVENT));
        addKeyword("emit", Integer.valueOf(sym.EMIT));
        addKeyword("var", Integer.valueOf(sym.VAR));
        addOperator("<identifier>", NAME);
        addOperator("<number>", NUMBER);
        addOperator("(", LPAR);
        addOperator(")", RPAR);
        addOperator("[]", SQBPAIR);
        addOperator("[", LSQB);
        addOperator("]", RSQB);
        addOperator(":", COLON);
        addOperator(",", COMMA);
        addOperator(";", SEMI);
        addOperator("+", PLUS);
        addOperator("-", MINUS);
        addOperator("*", STAR);
        addOperator("/", SLASH);
        addOperator("|", VBAR);
        addOperator("||", OR);
        addOperator("&", AMPER);
        addOperator("&&", AND);
        addOperator("!", NOT);
        addOperator("<", LESS);
        addOperator(">", GREATER);
        addOperator("=", EQUAL);
        addOperator(".", DOT);
        addOperator("%", PERCENT);
        addOperator("{", LEFT_BRACE);
        addOperator("}", RIGHT_BRACE);
        addOperator("^", CIRCUMFLEX);
        addOperator("~", TILDE);
        addOperator("@", AT);
        addOperator("==", EQEQUAL);
        addOperator("!=", NOTEQUAL);
        addOperator("<>", NOTEQUAL);
        addOperator("<=", LESSEQUAL);
        addOperator("<<", LEFTSHIFT);
        addOperator(">=", GREATEREQUAL);
        addOperator(">>", RIGHTSHIFT);
        addOperator("->", RIGHT_ARROW);
        addOperator("⨆", JOIN);
        addOperator("⨅", MEET);
        addOperator("=>", EQUALGREATER);
    }

%}

%init{
    init_keywords();
%init}

//, STRING

NEWLINE = \n | \r
digit = [0-9]
uppercase = [A-Z]
lowercase = [a-z]

letter = {uppercase}|{lowercase}
NAME = ( {letter} | "_") ({letter} | {digit} | "_")*
// comment_body = .*
// comment         = "//"{comment_body}

/* comments */
Comment = {TraditionalComment} | {EndOfLineComment} |
          {DocumentationComment}

TraditionalComment = "/*" [^*] ~"*/" | "/*" "*"+ "/"
InputCharacter = [^\r\n]
EndOfLineComment = "//" {InputCharacter}* {LineTerminator}?
DocumentationComment = "/*" "*"+ [^/*] ~"*/"

whitespace      = [ \n\t]
// encodingDeclaration = ""[^\n]*"coding"[:=][^\n]*
indent = \t

unicodeescape = "\\u"[a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9]
unicodeescape32 = "\\u"[a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9]
octescape = "\\"[0-7][0-7][0-7]
hexescape = "\\"[xX][0-9a-fA-F][0-9a-fA-F]

// number
NUMBER = {integer} | {floatnumber} | {imagnumber}

integer = {decinteger} | {bininteger} | {octinteger} | {hexinteger}
decinteger = {nonzerodigit} ("_"? {digit})* | "0"+ (["_"] "0")*
bininteger = "0" ("b" | "B") ("_"? {bindigit})+
octinteger = "0" ("o" | "O") ("_"? {octdigit})+
hexinteger = "0" ("x" | "X") ("_"? {hexdigit})+
nonzerodigit = [1-9]
bindigit = "0" | "1"
octdigit = [0-7]
hexdigit = {digit} | [a-f] | [A-F]

floatnumber = {pointfloat} | {exponentfloat}
pointfloat = {digitpart}? {fraction} | {digitpart} "."
exponentfloat = ({digitpart} | {pointfloat}) {exponent}
digitpart = {digit} ("_"? {digit})*
fraction = "." {digitpart}
exponent = ("e" | "E") ("+" | "-")? {digitpart}

imagnumber =  ({floatnumber} | {digitpart}) ("j" | "J")

invalid = "$" | "?" | "`"

StringCharacter = [^\r\n\"\\]
LineTerminator = \r|\n|\r\n


%state STRING

%%

<YYINITIAL> {
    {Comment}   {}
    {NAME}  { 
            Integer i = keywords.get(yytext());
            if (i == null) return id();
            else return key(i.intValue());
      }


    "("     {
            return op(sym.LPAR);
        }
    ")"     {
            return op(sym.RPAR); 
        }
    "[]"    { return op(sym.SQBPAIR); }
    "["     {
            return op(sym.LSQB); 
        }
    "]"     {
            return op(sym.RSQB); 
    }
    ":"     { return op(sym.COLON); }
    ","     { return op(sym.COMMA); }
    ";"     { return op(sym.SEMI); }
    "+"     { return op(sym.PLUS); }
    "-"     { return op(sym.MINUS); }
    "*"     { return op(sym.STAR); }
    "/"     { return op(sym.SLASH); }
    "|"     { return op(sym.VBAR); }
    "||"     { return op(sym.OR); }
    "&"     { return op(sym.AMPER); }
    "&&"     { return op(sym.AND); }
    "!"     { return op(sym.NOT); }
    "<"     { return op(sym.LESS); }
    ">"     { return op(sym.GREATER); }
    "="     { return op(sym.EQUAL); }
    "."     { return op(sym.DOT); }
    "%"     { return op(sym.PERCENT); }
    "{"     {
            return op(sym.LEFT_BRACE); 
    }
    "}"     {
            return op(sym.RIGHT_BRACE); 
        }
    "^"     { return op(sym.CIRCUMFLEX); }
    "~"     { return op(sym.TILDE); }
    "@"     { return op(sym.AT); }
    "=="    { return op(sym.EQEQUAL); }
    "!="    { return op(sym.NOTEQUAL); }
    "<>"    { return op(sym.NOTEQUAL); }
    "<="    { return op(sym.LESSEQUAL); }
    "<<"    { return op(sym.LEFTSHIFT); }
    ">="    { return op(sym.GREATEREQUAL); }
    ">>"    { return op(sym.RIGHTSHIFT); }
    //"+="    { return op(sym.PLUSEQUAL); }
    //"-="    { return op(sym.MINEQUAL); }
    "->"    { return op(sym.RIGHT_ARROW); }
    "⨆"      { return op(sym.JOIN); }
    "⨅"      { return op(sym.MEET); }
    //"*="    { return op(sym.STAREQUAL); }
    //"/="    { return op(sym.SLASHEQUAL); }
    //"|="    { return op(sym.VBAREQUAL); }
    //"%="    { return op(sym.PERCENTEQUAL); }
    // "&="    { return op(sym.AMPEREQUAL); }
    //"^="    { return op(sym.CIRCUMFLEXEQUAL); }
    //"@="    { return op(sym.ATEQUAL); }
    "&&"    { return op(sym.AND); }
    //"<<="   { return op(sym.LEFTSHIFTEQUAL); }
    //">>="   { return op(sym.RIGHTSHIFTEQUAL); }
    "=>"    { return op(sym.EQUALGREATER); }
    /* string literal */
    \"      { yybegin(STRING); sb.setLength(0); }

    {integer}   {
        return op(sym.NUMBER, yytext());
    }

    {NEWLINE} {
    }
    {indent}  {}

    {invalid} { return op(sym.ERROR, yytext()); }
    {whitespace} { }
    .       { return op(sym.ERROR, yytext()); }

    <<EOF>> {
            return op(sym.EOF);
    }
}

<STRING> {
    \"                             { yybegin(YYINITIAL); return op(STRING_LITERAL, sb.toString()); }

    {StringCharacter}+             { sb.append( yytext() ); }

    /* escape sequences */
    "\\b"                          { sb.append( '\b' ); }
    "\\t"                          { sb.append( '\t' ); }
    "\\n"                          { sb.append( '\n' ); }
    "\\f"                          { sb.append( '\f' ); }
    "\\r"                          { sb.append( '\r' ); }
    "\\\""                         { sb.append( '\"' ); }
    "\\'"                          { sb.append( '\'' ); }
    "\\\\"                         { sb.append( '\\' ); }
    \\[0-3]?{octdigit}?{octdigit}  { char val = (char) Integer.parseInt(yytext().substring(1),8);
                                                           sb.append( val ); }

    /* error cases */
    \\.                            { throw new RuntimeException("Illegal escape sequence \""+yytext()+"\""); }
    {LineTerminator}               { throw new RuntimeException("Unterminated string at end of line"); }
}


[^]         {
    return op(sym.ERROR, "Unrecognizable char: " + yytext());
}
