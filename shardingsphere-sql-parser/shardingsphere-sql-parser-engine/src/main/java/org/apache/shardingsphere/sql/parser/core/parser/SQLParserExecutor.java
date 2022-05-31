/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.sql.parser.core.parser;

import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.apache.shardingsphere.sql.parser.api.parser.SQLParser;
import org.apache.shardingsphere.sql.parser.core.ParseASTNode;
import org.apache.shardingsphere.sql.parser.exception.SQLParsingException;

/**
 * SQL parser executor.
 */
@RequiredArgsConstructor
public final class SQLParserExecutor {
    
    private final String databaseTypeName;
    
    private final String sql;
    
    /**
     * Execute to parse SQL. 生成解析树 ParseASTNode【真正解析】
     *
     * @return AST node
     */
    public ParseASTNode execute() {
        ParseASTNode result = towPhaseParse();
        if (result.getRootNode() instanceof ErrorNode) {
            throw new SQLParsingException(String.format("Unsupported SQL of `%s`", sql));
        }
        return result;
    }
    
    private ParseASTNode towPhaseParse() {
        SQLParser sqlParser = SQLParserFactory.newInstance(databaseTypeName, sql); // 创建该类型数据库对应的SQL解析器，由其工厂类SQLParserFactory类负责创建。
        try {
            ((Parser) sqlParser).setErrorHandler(new BailErrorStrategy());
            ((Parser) sqlParser).getInterpreter().setPredictionMode(PredictionMode.SLL);
            return (ParseASTNode) sqlParser.parse();
        } catch (final ParseCancellationException ex) {
            ((Parser) sqlParser).reset();
            ((Parser) sqlParser).setErrorHandler(new DefaultErrorStrategy());
            ((Parser) sqlParser).getInterpreter().setPredictionMode(PredictionMode.LL);
            return (ParseASTNode) sqlParser.parse();
        }
    }
}
