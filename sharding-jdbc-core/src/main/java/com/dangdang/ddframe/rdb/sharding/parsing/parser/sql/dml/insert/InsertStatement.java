/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.dangdang.ddframe.rdb.sharding.parsing.parser.sql.dml.insert;

import com.dangdang.ddframe.rdb.sharding.api.rule.ShardingRule;
import com.dangdang.ddframe.rdb.sharding.api.rule.TableRule;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Symbol;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.GeneratedKey;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.condition.Column;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.condition.Condition;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.condition.Conditions;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.expression.SQLNumberExpression;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.expression.SQLPlaceholderExpression;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.sql.dml.DMLStatement;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.GeneratedKeyToken;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.ItemsToken;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.SQLToken;
import com.google.common.base.Optional;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Insert statement. 插入SQL 解析结果
 *
 * @author zhangliang
 */
@Getter
@Setter
@ToString
public final class InsertStatement extends DMLStatement {
    // 插入字段
    private final Collection<Column> columns = new LinkedList<>();
    
    private final List<Conditions> multipleConditions = new LinkedList<>();
    // 插入字段 下一个Token 开始位置
    private int columnsListLastPosition;
    
    private int generateKeyColumnIndex = -1;
    
    private int afterValuesPosition;
    // 值字段 下一个Token 开始位置
    private int valuesListLastPosition;
    
    private GeneratedKey generatedKey;
    
    /**
     * Append generate key token. 追加自增主键标记对象
     *
     * @param shardingRule databases and tables sharding rule
     * @param parametersSize parameters size
     */
    public void appendGenerateKeyToken(final ShardingRule shardingRule, final int parametersSize) {
        if (null != generatedKey) {  // SQL 里有主键列
            return;
        }
        Optional<TableRule> tableRule = shardingRule.tryFindTableRule(getTables().getSingleTableName()); // TableRule 存在
        if (!tableRule.isPresent()) {
            return;
        }
        Optional<GeneratedKeyToken> generatedKeysToken = findGeneratedKeyToken(); // GeneratedKeyToken 存在
        if (!generatedKeysToken.isPresent()) {
            return;
        }
        ItemsToken valuesToken = new ItemsToken(generatedKeysToken.get().getBeginPosition()); // 处理 GenerateKeyToken
        if (0 == parametersSize) {
            appendGenerateKeyToken(shardingRule, tableRule.get(), valuesToken);
        } else {
            appendGenerateKeyToken(shardingRule, tableRule.get(), valuesToken, parametersSize);
        }
        getSqlTokens().remove(generatedKeysToken.get()); // 移除 generatedKeysToken
        getSqlTokens().add(valuesToken); // 新增 ItemsToken
    }
    // 占位符参数数量 = 0 时，直接生成分布式主键，保持无占位符的做法
    private void appendGenerateKeyToken(final ShardingRule shardingRule, final TableRule tableRule, final ItemsToken valuesToken) {
        Number generatedKey = shardingRule.generateKey(tableRule.getLogicTable());  // 生成分布式主键
        valuesToken.getItems().add(generatedKey.toString()); // 添加到 ItemsToken
        getConditions().add(new Condition(new Column(tableRule.getGenerateKeyColumn(), tableRule.getLogicTable()), new SQLNumberExpression(generatedKey)), shardingRule); // 增加 Condition，用于路由
        this.generatedKey = new GeneratedKey(tableRule.getLogicTable(), -1, generatedKey); // 生成 GeneratedKey
    }
    // 占位符参数数量 > 0 时，生成自增列的占位符，保持有占位符的做法
    private void appendGenerateKeyToken(final ShardingRule shardingRule, final TableRule tableRule, final ItemsToken valuesToken, final int parametersSize) {
        valuesToken.getItems().add(Symbol.QUESTION.getLiterals()); // 生成占位符
        getConditions().add(new Condition(new Column(tableRule.getGenerateKeyColumn(), tableRule.getLogicTable()), new SQLPlaceholderExpression(parametersSize)), shardingRule); // 增加 Condition，用于路由
        generatedKey = new GeneratedKey(tableRule.getGenerateKeyColumn(), parametersSize, null); // 生成 GeneratedKey
    }
    
    private Optional<GeneratedKeyToken> findGeneratedKeyToken() {
        for (SQLToken each : getSqlTokens()) {
            if (each instanceof GeneratedKeyToken) {
                return Optional.of((GeneratedKeyToken) each);
            }
        }
        return Optional.absent();
    }
}
