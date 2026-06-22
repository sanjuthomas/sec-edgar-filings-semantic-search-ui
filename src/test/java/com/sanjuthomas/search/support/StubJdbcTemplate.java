package com.sanjuthomas.search.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class StubJdbcTemplate extends JdbcTemplate {

    private final Function<String, ResultSet> rowFactory;
    private String lastSql;
    private Object[] lastArgs;

    public StubJdbcTemplate(Function<String, ResultSet> rowFactory) {
        this.rowFactory = rowFactory;
    }

    public StubJdbcTemplate(Map<String, Object> columns) {
        this(sql -> ResultSetProxy.of(columns));
    }

    public String lastSql() {
        return lastSql;
    }

    public Object[] lastArgs() {
        return lastArgs;
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
        return query(sql, rowMapper, new Object[0]);
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        lastSql = sql;
        lastArgs = args;
        try {
            return List.of(rowMapper.mapRow(rowFactory.apply(sql), 0));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public <T> List<T> queryForList(String sql, Class<T> elementType) {
        return queryForList(sql, elementType, new Object[0]);
    }

    @Override
    public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
        lastSql = sql;
        lastArgs = args;
        if (elementType == String.class) {
            @SuppressWarnings("unchecked")
            T value = (T) "GS";
            return List.of(value);
        }
        throw new UnsupportedOperationException("Unsupported element type: " + elementType);
    }
}
