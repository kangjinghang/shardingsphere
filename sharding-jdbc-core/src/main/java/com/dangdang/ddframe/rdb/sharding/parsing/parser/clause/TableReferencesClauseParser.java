package com.dangdang.ddframe.rdb.sharding.parsing.parser.clause;

import com.dangdang.ddframe.rdb.sharding.api.rule.ShardingRule;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.LexerEngine;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.DefaultKeyword;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Keyword;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Symbol;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.table.Table;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.sql.SQLStatement;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.TableToken;
import com.dangdang.ddframe.rdb.sharding.util.SQLUtil;
import com.google.common.base.Optional;
import lombok.Getter;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Table references clause parser.
 *
 * @author zhangliang
 */
public class TableReferencesClauseParser implements SQLClauseParser {
    
    private final ShardingRule shardingRule;
    
    @Getter
    private final LexerEngine lexerEngine;
    
    private final AliasClauseParser aliasClauseParser;
    
    private final ExpressionClauseParser expressionClauseParser;
    
    public TableReferencesClauseParser(final ShardingRule shardingRule, final LexerEngine lexerEngine) {
        this.shardingRule = shardingRule;
        this.lexerEngine = lexerEngine;
        aliasClauseParser = new AliasClauseParser(lexerEngine);
        expressionClauseParser = new ExpressionClauseParser(lexerEngine);
    }
    
    /**
     * Parse table references.
     *
     * @param sqlStatement SQL statement
     * @param isSingleTableOnly is parse single table only
     */
    public final void parse(final SQLStatement sqlStatement, final boolean isSingleTableOnly) {
        do {
            parseTableReference(sqlStatement, isSingleTableOnly);
        } while (lexerEngine.skipIfEqual(Symbol.COMMA));
    }
    
    protected void parseTableReference(final SQLStatement sqlStatement, final boolean isSingleTableOnly) {
        parseTableFactor(sqlStatement, isSingleTableOnly);
    }
    // 解析单个表名和表别名
    protected final void parseTableFactor(final SQLStatement sqlStatement, final boolean isSingleTableOnly) {
        final int beginPosition = lexerEngine.getCurrentToken().getEndPosition() - lexerEngine.getCurrentToken().getLiterals().length();
        String literals = lexerEngine.getCurrentToken().getLiterals();
        lexerEngine.nextToken();
        if (lexerEngine.equalAny(Symbol.DOT)) {
            throw new UnsupportedOperationException("Cannot support SQL for `schema.table`");
        }
        String tableName = SQLUtil.getExactlyValue(literals);
        Optional<String> alias = aliasClauseParser.parse();
        if (isSingleTableOnly || shardingRule.tryFindTableRule(tableName).isPresent() || shardingRule.findBindingTableRule(tableName).isPresent()
                || shardingRule.getDataSourceRule().getDefaultDataSource().isPresent()) {
            sqlStatement.getSqlTokens().add(new TableToken(beginPosition, literals));
            sqlStatement.getTables().add(new Table(tableName, alias));  // 表 以及 表别名
        }
        parseJoinTable(sqlStatement); // 这里调用 parseJoinTable() 而不是 parseTableFactor() ：下一个 Table 可能是子查询。 例如：SELECT * FROM t_order JOIN (SELECT * FROM t_order_item JOIN t_order_other ON )
        if (isSingleTableOnly && !sqlStatement.getTables().isSingleTable()) {
            throw new UnsupportedOperationException("Cannot support Multiple-Table.");
        }
    }
    // 解析 Join Table 或者 FROM 下一张 Table
    private void parseJoinTable(final SQLStatement sqlStatement) {
        while (parseJoinType()) {  // 继续循环
            if (lexerEngine.equalAny(Symbol.LEFT_PAREN)) {
                throw new UnsupportedOperationException("Cannot support sub query for join table.");
            }
            parseTableFactor(sqlStatement, false);
            parseJoinCondition(sqlStatement);
        }
    }
    
    private boolean parseJoinType() {
        List<Keyword> joinTypeKeywords = new LinkedList<>();
        joinTypeKeywords.addAll(Arrays.asList(
                DefaultKeyword.INNER, DefaultKeyword.OUTER, DefaultKeyword.LEFT, DefaultKeyword.RIGHT, DefaultKeyword.FULL, DefaultKeyword.CROSS, DefaultKeyword.NATURAL, DefaultKeyword.JOIN));
        joinTypeKeywords.addAll(Arrays.asList(getKeywordsForJoinType()));
        Keyword[] joinTypeKeywordArrays = joinTypeKeywords.toArray(new Keyword[joinTypeKeywords.size()]);
        if (!lexerEngine.equalAny(joinTypeKeywordArrays)) {
            return false;
        }
        lexerEngine.skipAll(joinTypeKeywordArrays);
        return true;
    }
    
    protected Keyword[] getKeywordsForJoinType() {
        return new Keyword[0];
    }
    
    private void parseJoinCondition(final SQLStatement sqlStatement) {
        if (lexerEngine.skipIfEqual(DefaultKeyword.ON)) {  // JOIN 表时 ON 条件
            do {
                expressionClauseParser.parse(sqlStatement);
                lexerEngine.accept(Symbol.EQ);
                expressionClauseParser.parse(sqlStatement);
            } while (lexerEngine.skipIfEqual(DefaultKeyword.AND));
        } else if (lexerEngine.skipIfEqual(DefaultKeyword.USING)) { // JOIN 表时 USING 为使用两表相同字段相同时对 ON 的简化。例如以下两条 SQL 等价：
            // SELECT * FROM t_order o JOIN t_order_item i USING (order_id);
            // SELECT * FROM t_order o JOIN t_order_item i ON o.order_id = i.order_id
            lexerEngine.skipParentheses(sqlStatement);
        }
    }
}
