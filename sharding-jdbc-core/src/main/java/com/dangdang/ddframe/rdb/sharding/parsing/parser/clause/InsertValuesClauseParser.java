package com.dangdang.ddframe.rdb.sharding.parsing.parser.clause;

import com.dangdang.ddframe.rdb.sharding.api.rule.ShardingRule;
import com.dangdang.ddframe.rdb.sharding.exception.ShardingJdbcException;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.LexerEngine;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.DefaultKeyword;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Keyword;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Symbol;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.GeneratedKey;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.condition.Column;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.condition.Condition;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.condition.Conditions;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.expression.SQLExpression;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.expression.SQLNumberExpression;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.expression.SQLPlaceholderExpression;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.sql.dml.insert.InsertStatement;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.MultipleInsertValuesToken;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Insert values clause parser.
 *
 * @author zhangliang
 */
public class InsertValuesClauseParser implements SQLClauseParser {
    
    private final ShardingRule shardingRule;
    
    private final LexerEngine lexerEngine;
    
    private final ExpressionClauseParser expressionClauseParser;
    
    public InsertValuesClauseParser(final ShardingRule shardingRule, final LexerEngine lexerEngine) {
        this.shardingRule = shardingRule;
        this.lexerEngine = lexerEngine;
        expressionClauseParser = new ExpressionClauseParser(lexerEngine);
    }
    
    /**
     * Parse insert values. 解析值字段
     *
     * @param insertStatement insert statement
     */
    public void parse(final InsertStatement insertStatement) {
        Collection<Keyword> valueKeywords = new LinkedList<>();
        valueKeywords.add(DefaultKeyword.VALUES);
        valueKeywords.addAll(Arrays.asList(getSynonymousKeywordsForValues()));
        if (lexerEngine.skipIfEqual(valueKeywords.toArray(new Keyword[valueKeywords.size()]))) {
            insertStatement.setAfterValuesPosition(lexerEngine.getCurrentToken().getEndPosition() - lexerEngine.getCurrentToken().getLiterals().length());
            parseValues(insertStatement);
            if (lexerEngine.equalAny(Symbol.COMMA)) {
                parseMultipleValues(insertStatement);
            }
        }
    }
    
    protected Keyword[] getSynonymousKeywordsForValues() {
        return new Keyword[0];
    }
    // 解析值字段
    private void parseValues(final InsertStatement insertStatement) {
        lexerEngine.accept(Symbol.LEFT_PAREN);
        List<SQLExpression> sqlExpressions = new LinkedList<>();  // 解析表达式
        do {
            sqlExpressions.add(expressionClauseParser.parse(insertStatement));
        } while (lexerEngine.skipIfEqual(Symbol.COMMA));
        insertStatement.setValuesListLastPosition(lexerEngine.getCurrentToken().getEndPosition() - lexerEngine.getCurrentToken().getLiterals().length());
        int count = 0;
        for (Column each : insertStatement.getColumns()) {  // 解析值字段
            SQLExpression sqlExpression = sqlExpressions.get(count);
            insertStatement.getConditions().add(new Condition(each, sqlExpression), shardingRule);
            if (insertStatement.getGenerateKeyColumnIndex() == count) {  // 自动生成键
                insertStatement.setGeneratedKey(createGeneratedKey(each, sqlExpression));
            }
            count++;
        }
        lexerEngine.accept(Symbol.RIGHT_PAREN); // 字段以 "," 分隔
    }
    // 创建 自动生成键
    private GeneratedKey createGeneratedKey(final Column column, final SQLExpression sqlExpression) {
        GeneratedKey result;
        if (sqlExpression instanceof SQLPlaceholderExpression) {  // 占位符
            result = new GeneratedKey(column.getName(), ((SQLPlaceholderExpression) sqlExpression).getIndex(), null);
        } else if (sqlExpression instanceof SQLNumberExpression) { // 数字
            result = new GeneratedKey(column.getName(), -1, ((SQLNumberExpression) sqlExpression).getNumber());
        } else {
            throw new ShardingJdbcException("Generated key only support number.");
        }
        return result;
    }
    
    private void parseMultipleValues(final InsertStatement insertStatement) {
        insertStatement.getMultipleConditions().add(new Conditions(insertStatement.getConditions()));
        MultipleInsertValuesToken valuesToken = new MultipleInsertValuesToken(insertStatement.getAfterValuesPosition());
        valuesToken.getValues().add(
                lexerEngine.getInput().substring(insertStatement.getAfterValuesPosition(), lexerEngine.getCurrentToken().getEndPosition() - Symbol.COMMA.getLiterals().length()));
        while (lexerEngine.skipIfEqual(Symbol.COMMA)) {
            int beginPosition = lexerEngine.getCurrentToken().getEndPosition() - lexerEngine.getCurrentToken().getLiterals().length();
            parseValues(insertStatement);
            insertStatement.getMultipleConditions().add(new Conditions(insertStatement.getConditions()));
            int endPosition = lexerEngine.equalAny(Symbol.COMMA)
                    ? lexerEngine.getCurrentToken().getEndPosition() - Symbol.COMMA.getLiterals().length() : lexerEngine.getCurrentToken().getEndPosition();
            valuesToken.getValues().add(lexerEngine.getInput().substring(beginPosition, endPosition));
        }
        insertStatement.getSqlTokens().add(valuesToken);
    }
}
