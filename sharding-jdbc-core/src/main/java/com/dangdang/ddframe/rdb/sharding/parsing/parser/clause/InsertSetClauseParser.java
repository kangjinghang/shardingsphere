package com.dangdang.ddframe.rdb.sharding.parsing.parser.clause;

import com.dangdang.ddframe.rdb.sharding.api.rule.ShardingRule;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.LexerEngine;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Assist;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.DefaultKeyword;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Keyword;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Literals;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Symbol;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.condition.Column;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.condition.Condition;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.expression.SQLExpression;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.expression.SQLIgnoreExpression;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.expression.SQLNumberExpression;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.expression.SQLPlaceholderExpression;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.expression.SQLTextExpression;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.sql.dml.insert.InsertStatement;
import com.dangdang.ddframe.rdb.sharding.util.SQLUtil;
import lombok.RequiredArgsConstructor;

/**
 * Insert set clause parser.
 *
 * @author zhangliang
 */
@RequiredArgsConstructor
public class InsertSetClauseParser implements SQLClauseParser {
    
    private final ShardingRule shardingRule;
    
    private final LexerEngine lexerEngine;
    
    /**
     * Parse insert set. 例如：INSERT INTO test SET id = 4  ON DUPLICATE KEY UPDATE name = 'doubi', name = 'hehe'; INSERT INTO test SET id = 4, name = 'hehe';
     *
     * @param insertStatement insert statement
     */
    public void parse(final InsertStatement insertStatement) {
        if (!lexerEngine.skipIfEqual(getCustomizedInsertKeywords())) {
            return;
        }
        do {
            Column column = new Column(SQLUtil.getExactlyValue(lexerEngine.getCurrentToken().getLiterals()), insertStatement.getTables().getSingleTableName()); // 插入字段
            lexerEngine.nextToken();
            lexerEngine.accept(Symbol.EQ);  // 等号
            SQLExpression sqlExpression;  // 【值】表达式
            if (lexerEngine.equalAny(Literals.INT)) {
                sqlExpression = new SQLNumberExpression(Integer.parseInt(lexerEngine.getCurrentToken().getLiterals()));
            } else if (lexerEngine.equalAny(Literals.FLOAT)) {
                sqlExpression = new SQLNumberExpression(Double.parseDouble(lexerEngine.getCurrentToken().getLiterals()));
            } else if (lexerEngine.equalAny(Literals.CHARS)) {
                sqlExpression = new SQLTextExpression(lexerEngine.getCurrentToken().getLiterals());
            } else if (lexerEngine.equalAny(DefaultKeyword.NULL)) {
                sqlExpression = new SQLIgnoreExpression(DefaultKeyword.NULL.name());
            } else if (lexerEngine.equalAny(Symbol.QUESTION)) {
                sqlExpression = new SQLPlaceholderExpression(insertStatement.getParametersIndex());
                insertStatement.increaseParametersIndex();
            } else {
                throw new UnsupportedOperationException("");
            }
            lexerEngine.nextToken();
            if (lexerEngine.equalAny(Symbol.COMMA, DefaultKeyword.ON, Assist.END)) {
                insertStatement.getConditions().add(new Condition(column, sqlExpression), shardingRule);
            } else {
                lexerEngine.skipUntil(Symbol.COMMA, DefaultKeyword.ON);
            }
        } while (lexerEngine.skipIfEqual(Symbol.COMMA));  // 字段以 "," 分隔
    }
    
    protected Keyword[] getCustomizedInsertKeywords() {
        return new Keyword[0];
    }
}
