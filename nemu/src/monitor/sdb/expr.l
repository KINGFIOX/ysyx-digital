/* prefix="sdb_expr": 定义前缀, yy_scan_string -> sdb_expr_scan_string, etc. */
%option noinput nounput noyywrap nodefault prefix="sdb_expr"

%{
#include <stdlib.h>
#include <common.h>
#include <isa.h>
#include "expr.tab.h"

#define yylval sdb_exprlval

extern bool sdb_expr_lexer_error;
%}

%%
[ \t\r\n]+                ; /* 空白操作符 */
0[xX][0-9a-fA-F]+         { yylval = strtoull(yytext, NULL, 16); return TK_HEX; } /* 十六进制 */
[0-9]+                    { yylval = strtoull(yytext, NULL, 10); return TK_DEC; } /* 十进制 */
\$[a-zA-Z0-9_]+           {
                            bool ok = false;
                            word_t v = isa_reg_str2val(yytext + 1 /* 去掉前缀 $ */ , &ok);
                            if (!ok) { sdb_expr_lexer_error = true; }
                            yylval = v;
                            return TK_REG;
                          }
"=="                      { return EQ; }
"!="                      { return NE; }
"<"                       { return LT; }
"<="                      { return LE; }
">"                       { return GT; }
">="                      { return GE; }
"&&"                      { return AND; }
"||"                      { return OR; }
"+"|"-"|"*"|"/"|"("|")"   { return yytext[0]; } /* 返回符号本身 */
.                         { sdb_expr_lexer_error = true; return yytext[0]; } /* 未知字符 */
%%


