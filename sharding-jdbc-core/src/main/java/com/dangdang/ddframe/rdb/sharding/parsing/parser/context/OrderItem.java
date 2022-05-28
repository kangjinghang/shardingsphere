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

package com.dangdang.ddframe.rdb.sharding.parsing.parser.context;

import com.dangdang.ddframe.rdb.sharding.constant.OrderType;
import com.google.common.base.Optional;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Order item. 排序项。属于分片上下文信息
 *
 * @author zhangliang
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
public final class OrderItem {
    // 所属表别名
    private final Optional<String> owner;
    // 排序字段
    private final Optional<String> name;
    // 排序类型
    private final OrderType type;
    
    private final OrderType nullOrderType;
    // 按照第几个查询字段排序。ORDER BY 数字 的 数字代表的是第几个字段
    private int index = -1;
    // 字段在查询项({@link SelectItem} 的别名
    private Optional<String> alias;
    
    public OrderItem(final String name, final OrderType type, final OrderType nullOrderType, final Optional<String> alias) {
        this.owner = Optional.absent();
        this.name = Optional.of(name);
        this.type = type;
        this.nullOrderType = nullOrderType;
        this.alias = alias;
    }
    
    public OrderItem(final String owner, final String name, final OrderType type, final OrderType nullOrderType, final Optional<String> alias) {
        this.owner = Optional.of(owner);
        this.name = Optional.of(name);
        this.type = type;
        this.nullOrderType = nullOrderType;
        this.alias = alias;
    }
    
    public OrderItem(final int index, final OrderType type, final OrderType nullOrderType) {
        owner = Optional.absent();
        name = Optional.absent();
        this.index = index;
        this.type = type;
        this.nullOrderType = nullOrderType;
        alias = Optional.absent();
    }
    
    /**
     * Get column label.
     *
     * @return column label
     */
    public String getColumnLabel() {
        return alias.isPresent() ? alias.get() : name.orNull();
    }
    
    /**
     * Get qualified name.
     *
     * @return qualified name
     */
    public Optional<String> getQualifiedName() {
        if (!name.isPresent()) {
            return Optional.absent();
        }
        return owner.isPresent() ? Optional.of(owner.get() + "." + name.get()) : name;
    }
}
