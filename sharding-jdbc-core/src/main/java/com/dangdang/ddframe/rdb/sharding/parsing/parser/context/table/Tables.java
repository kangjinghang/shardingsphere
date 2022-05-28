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

package com.dangdang.ddframe.rdb.sharding.parsing.parser.context.table;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Tables collection. 表集合对象。属于分片上下文信息
 * 
 * @author zhangliang
 */
@ToString
public final class Tables {
    
    private final List<Table> tables = new ArrayList<>();
    
    /**
     * 添加表解析对象.
     * 
     * @param table 表对象
     */
    public void add(final Table table) {
        tables.add(table);
    }
    
    /**
     * 判断是否为空.
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return tables.isEmpty();
    }
    
    /**
     * 判断是否为单表.
     * 
     * @return 是否为单表
     */
    public boolean isSingleTable() {
        return 1 == tables.size();
    }
    
    /**
     * 获取表名称.
     *
     * @return 表名称
     */
    public String getSingleTableName() {
        Preconditions.checkArgument(!isEmpty());
        return tables.get(0).getName();
    }
    
    /**
     * 获取表名称集合.
     * 
     * @return 表名称集合
     */
    public Collection<String> getTableNames() {
        Collection<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Table each : tables) {
            result.add(each.getName());
        }
        return result;
    }
    
    /**
     * 根据表名称或别名查找表解析对象.
     * 
     * @param tableNameOrAlias 表名称或别名
     * @return 表解析对象
     */
    public Optional<Table> find(final String tableNameOrAlias) {
        Optional<Table> tableFromName = findTableFromName(tableNameOrAlias);
        return tableFromName.isPresent() ? tableFromName : findTableFromAlias(tableNameOrAlias);
    }
    
    private Optional<Table> findTableFromName(final String name) {
        for (Table each : tables) {
            if (each.getName().equalsIgnoreCase(name)) {
                return Optional.of(each);
            }
        }
        return Optional.absent();
    }
    
    private Optional<Table> findTableFromAlias(final String alias) {
        for (Table each : tables) {
            if (each.getAlias().isPresent() && each.getAlias().get().equalsIgnoreCase(alias)) {
                return Optional.of(each);
            }
        }
        return Optional.absent();
    }
}
