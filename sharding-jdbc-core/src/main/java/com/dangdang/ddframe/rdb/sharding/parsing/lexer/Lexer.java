/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.dangdang.ddframe.rdb.sharding.parsing.lexer;

import com.dangdang.ddframe.rdb.sharding.parsing.lexer.analyzer.CharType;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.analyzer.Dictionary;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.analyzer.Tokenizer;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Assist;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Token;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Lexical analysis.
 * 
 * @author zhangliang 
 */
@RequiredArgsConstructor
public class Lexer {
    // 输出字符串，比如：SQL
    @Getter
    private final String input;
    // 词法标记字典
    private final Dictionary dictionary;
    // 解析到 SQL 的 offset
    private int offset;
    //  当前 词法标记
    @Getter
    private Token currentToken;
    
    /**
     * Analyse next token. 分析下一个词法标记
     */
    public final void nextToken() {
        skipIgnoredToken(); // 跳过忽略的 Token。通过 #isXXXX() 方法判断好下一个 Token 的类型后，交给 Tokenizer 进行分词返回 Token
        if (isVariableBegin()) {  // 变量
            currentToken = new Tokenizer(input, dictionary, offset).scanVariable();
        } else if (isNCharBegin()) {  // N\
            currentToken = new Tokenizer(input, dictionary, ++offset).scanChars();
        } else if (isIdentifierBegin()) { // Keyword + Literals.IDENTIFIER
            currentToken = new Tokenizer(input, dictionary, offset).scanIdentifier();
        } else if (isHexDecimalBegin()) { // 十六进制
            currentToken = new Tokenizer(input, dictionary, offset).scanHexDecimal();
        } else if (isNumberBegin()) { // 数字（整数+浮点数）
            currentToken = new Tokenizer(input, dictionary, offset).scanNumber();
        } else if (isSymbolBegin()) { // 符号
            currentToken = new Tokenizer(input, dictionary, offset).scanSymbol();
        } else if (isCharsBegin()) { // 字符串，例如："abc"
            currentToken = new Tokenizer(input, dictionary, offset).scanChars();
        } else if (isEnd()) { // 结束
            currentToken = new Token(Assist.END, "", offset);
        } else { // 分析错误，无符合条件的词法标记
            currentToken = new Token(Assist.ERROR, "", offset);
        }
        offset = currentToken.getEndPosition();
    }
    // 跳过忽略的词法标记。1. 空格 2. SQL Hint 3. SQL 注释
    private void skipIgnoredToken() {
        offset = new Tokenizer(input, dictionary, offset).skipWhitespace(); // 空格
        while (isHintBegin()) { // SQL Hint
            offset = new Tokenizer(input, dictionary, offset).skipHint();
            offset = new Tokenizer(input, dictionary, offset).skipWhitespace();
        }
        while (isCommentBegin()) { // SQL 注释
            offset = new Tokenizer(input, dictionary, offset).skipComment();
            offset = new Tokenizer(input, dictionary, offset).skipWhitespace();
        }
    }
    
    protected boolean isHintBegin() {
        return false;
    }
    
    protected boolean isCommentBegin() {
        char current = getCurrentChar(0);
        char next = getCurrentChar(1);
        return '/' == current && '/' == next || '-' == current && '-' == next || '/' == current && '*' == next;
    }
    // 是否是 变量。MySQL 与 SQL Server 支持
    protected boolean isVariableBegin() {
        return false;
    }
    
    protected boolean isSupportNChars() {
        return false;
    }
    // 是否 N\。目前 SQLServer 独有：在 SQL Server 中处理 Unicode 字串常数時，必须为所有的 Unicode 字串加上前置词 N
    private boolean isNCharBegin() {
        return isSupportNChars() && 'N' == getCurrentChar(0) && '\'' == getCurrentChar(1);
    }
    
    private boolean isIdentifierBegin() {
        return isIdentifierBegin(getCurrentChar(0));
    }
    
    private boolean isIdentifierBegin(final char ch) {
        return CharType.isAlphabet(ch) || '`' == ch || '_' == ch || '$' == ch;
    }
    //  是否是 十六进制
    private boolean isHexDecimalBegin() {
        return '0' == getCurrentChar(0) && 'x' == getCurrentChar(1);
    }
    // 是否是 数字。'-' 需要特殊处理。".2" 被处理成省略0的小数，"-.2" 不能被处理成省略的小数，否则会出问题。例如说，"SELECT a-.2" 处理的结果是 "SELECT" / "a" / "-" / ".2"
    private boolean isNumberBegin() {  // 数字 || 浮点数 || 负数
        return CharType.isDigital(getCurrentChar(0)) || ('.' == getCurrentChar(0) && CharType.isDigital(getCurrentChar(1)) && !isIdentifierBegin(getCurrentChar(-1))
                || ('-' == getCurrentChar(0) && ('.' == getCurrentChar(0) || CharType.isDigital(getCurrentChar(1)))));
    }
    // 是否是 符号
    private boolean isSymbolBegin() {
        return CharType.isSymbol(getCurrentChar(0));
    }
    
    private boolean isCharsBegin() {
        return '\'' == getCurrentChar(0) || '\"' == getCurrentChar(0);
    }
    
    private boolean isEnd() {
        return offset >= input.length();
    }
    
    protected final char getCurrentChar(final int offset) {
        return this.offset + offset >= input.length() ? (char) CharType.EOI : input.charAt(this.offset + offset);
    }
}
