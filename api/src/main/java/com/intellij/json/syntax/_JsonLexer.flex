package com.intellij.json.syntax;

import consulo.language.ast.TokenType;
import consulo.language.lexer.FlexLexer;
import consulo.language.ast.IElementType;
import com.intellij.json.JsonElementTypes;

import static consulo.language.ast.TokenType.WHITE_SPACE;
import static consulo.language.ast.TokenType.BAD_CHARACTER;

%%

%public
%class _JsonLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

LINE_COMMENT="//".*
BLOCK_COMMENT="/"\*([^*]|\*+[^*/])*(\*+"/")?
DOUBLE_QUOTED_STRING=\"([^\\\"\r\n]|\\[^\r\n])*\"?
SINGLE_QUOTED_STRING='([^\\'\r\n]|\\[^\r\n])*'?
NUMBER=(-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]*)?)|Infinity|-Infinity|NaN
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
