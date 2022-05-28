package com.dangdang.ddframe.rdb.sharding.parsing.parser.dialect.oracle.clause;

import com.dangdang.ddframe.rdb.sharding.api.rule.ShardingRule;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.LexerEngine;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.dialect.oracle.OracleKeyword;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Keyword;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.clause.SelectListClauseParser;

/**
 * Select list clause parser for Oracle.
 *
 * @author zhangliang
 */
public final class OracleSelectListClauseParser extends SelectListClauseParser {
    
    public OracleSelectListClauseParser(final ShardingRule shardingRule, final LexerEngine lexerEngine) {
        super(shardingRule, lexerEngine);
    }
    // Oracle 独有：https://docs.oracle.com/cd/B19306_01/server.102/b14200/operators004.htm
    @Override
    protected Keyword[] getSkippedKeywordsBeforeSelectItem() {
        return new Keyword[] {OracleKeyword.CONNECT_BY_ROOT};
    }
}
