package com.dangdang.ddframe.rdb.sharding.parsing.parser.clause;

import com.dangdang.ddframe.rdb.sharding.parsing.lexer.LexerEngine;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.DefaultKeyword;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Keyword;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Symbol;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.sql.dml.insert.InsertStatement;
import lombok.RequiredArgsConstructor;

/**
 * Insert into clause parser.
 *
 * @author zhangliang
 */
@RequiredArgsConstructor
public class InsertIntoClauseParser implements SQLClauseParser {
    
    private final LexerEngine lexerEngine;
    
    private final TableReferencesClauseParser tableReferencesClauseParser;
    
    /**
     * Parse insert into.
     *
     * @param insertStatement insert statement
     */
    public void parse(final InsertStatement insertStatement) {
        lexerEngine.unsupportedIfEqual(getUnsupportedKeywordsBeforeInto());  // 例如，Oracle，INSERT FIRST/ALL 目前不支持
        lexerEngine.skipUntil(DefaultKeyword.INTO);
        lexerEngine.nextToken();
        tableReferencesClauseParser.parse(insertStatement, true); // 解析表
        skipBetweenTableAndValues(insertStatement); // 跳过 表 和 插入字段 中间的 Token
    }
    
    protected Keyword[] getUnsupportedKeywordsBeforeInto() {
        return new Keyword[0];
    }
    // 跳过 表 和 插入字段 中间的 Token。例如 MySQL ：[PARTITION (partition_name,...)]
    private void skipBetweenTableAndValues(final InsertStatement insertStatement) {
        while (lexerEngine.skipIfEqual(getSkippedKeywordsBetweenTableAndValues())) {
            lexerEngine.nextToken();
            if (lexerEngine.equalAny(Symbol.LEFT_PAREN)) {
                lexerEngine.skipParentheses(insertStatement);
            }
        }
    }
    
    protected Keyword[] getSkippedKeywordsBetweenTableAndValues() {
        return new Keyword[0];
    }
}
