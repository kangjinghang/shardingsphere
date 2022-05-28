package com.dangdang.ddframe.rdb.sharding.parsing.parser.clause;

import com.dangdang.ddframe.rdb.sharding.parsing.lexer.LexerEngine;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.DefaultKeyword;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Symbol;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.sql.dml.DMLStatement;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.TableToken;
import com.dangdang.ddframe.rdb.sharding.util.SQLUtil;

/**
 * Update set items clause parser.
 *
 * @author zhangliang
 */
public final class UpdateSetItemsClauseParser implements SQLClauseParser {
    
    private final LexerEngine lexerEngine;
    
    private final ExpressionClauseParser expressionClauseParser;
    
    public UpdateSetItemsClauseParser(final LexerEngine lexerEngine) {
        this.lexerEngine = lexerEngine;
        expressionClauseParser = new ExpressionClauseParser(lexerEngine);
    }
    
    /**
     * Parse set items. 解析多个 SET 项
     *
     * @param updateStatement DML statement
     */
    public void parse(final DMLStatement updateStatement) {
        lexerEngine.accept(DefaultKeyword.SET);
        do {
            parseSetItem(updateStatement);
        } while (lexerEngine.skipIfEqual(Symbol.COMMA)); // 以 "," 分隔
    }
    // 解析单个 SET 项
    private void parseSetItem(final DMLStatement updateStatement) {
        parseSetColumn(updateStatement);
        lexerEngine.skipIfEqual(Symbol.EQ, Symbol.COLON_EQ);
        parseSetValue(updateStatement);
    }
    // 解析单个 SET 项
    private void parseSetColumn(final DMLStatement updateStatement) {
        if (lexerEngine.equalAny(Symbol.LEFT_PAREN)) {
            lexerEngine.skipParentheses(updateStatement);
            return;
        }
        int beginPosition = lexerEngine.getCurrentToken().getEndPosition();
        String literals = lexerEngine.getCurrentToken().getLiterals();
        lexerEngine.nextToken();
        if (lexerEngine.skipIfEqual(Symbol.DOT)) {  // 字段有别名
            if (updateStatement.getTables().getSingleTableName().equalsIgnoreCase(SQLUtil.getExactlyValue(literals))) {
                updateStatement.getSqlTokens().add(new TableToken(beginPosition - literals.length(), literals));
            }
            lexerEngine.nextToken();
        }
    }
    // 解析单个 SET 值
    private void parseSetValue(final DMLStatement updateStatement) {
        expressionClauseParser.parse(updateStatement);
    }
}
