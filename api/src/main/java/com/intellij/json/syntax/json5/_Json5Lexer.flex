package com.intellij.json.syntax.json5;

import consulo.language.ast.TokenType;
import consulo.language.lexer.FlexLexer;
import consulo.language.ast.IElementType;
import com.intellij.json.JsonElementTypes;

import static consulo.language.ast.TokenType.WHITE_SPACE;
import static consulo.language.ast.TokenType.BAD_CHARACTER;

%%

%public
%class _Json5Lexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+
HEX_DIGIT=[0-9A-Fa-f]

LINE_COMMENT="//".*
BLOCK_COMMENT="/"\*([^*]|\*+[^*/])*(\*+"/")?
LINE_TERMINATOR_SEQUENCE=\R
CRLF= [\ \t \f]* {LINE_TERMINATOR_SEQUENCE}
DOUBLE_QUOTED_STRING=\"([^\\\"\r\n]|\\[^\r\n]|\\{CRLF})*\"?
SINGLE_QUOTED_STRING='([^\\'\r\n]|\\[^\r\n]|\\{CRLF})*'?
JSON5_NUMBER=(\+|-)?(0|[1-9][0-9]*)?\.?([0-9]+)?([eE][+-]?[0-9]*)?
HEX_DIGITS=({HEX_DIGIT})+
HEX_INTEGER_LITERAL=(\+|-)?0[Xx]({HEX_DIGITS})
NUMBER={JSON5_NUMBER}|{HEX_INTEGER_LITERAL}|Infinity|-Infinity|\+Infinity|NaN|-NaN|\+NaN
IDENTIFIER=[[:jletterdigit:]~!()*\-."/"@\^<>=]+

%%
<YYINITIAL> {
  {WHITE_SPACE}               { return WHITE_SPACE; }

  "{"                         { return JsonElementTypes.L_CURLY; }
  "}"                         { return JsonElementTypes.R_CURLY; }
  "["                         { return JsonElementTypes.L_BRACKET; }
  "]"                         { return JsonElementTypes.R_BRACKET; }
  ","                         { return JsonElementTypes.COMMA; }
  ":"                         { return JsonElementTypes.COLON; }
  "true"                      { return JsonElementTypes.TRUE; }
  "false"                     { return JsonElementTypes.FALSE; }
  "null"                      { return JsonElementTypes.NULL; }

  {LINE_COMMENT}              { return JsonElementTypes.LINE_COMMENT; }
  {BLOCK_COMMENT}             { return JsonElementTypes.BLOCK_COMMENT; }
  {DOUBLE_QUOTED_STRING}      { return JsonElementTypes.DOUBLE_QUOTED_STRING; }
  {SINGLE_QUOTED_STRING}      { return JsonElementTypes.SINGLE_QUOTED_STRING; }
  {NUMBER}                    { return JsonElementTypes.NUMBER; }
  {IDENTIFIER}                { return JsonElementTypes.IDENTIFIER; }

}

[^] { return BAD_CHARACTER; }
