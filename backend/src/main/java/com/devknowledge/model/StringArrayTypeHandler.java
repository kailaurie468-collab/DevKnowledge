package com.devknowledge.model;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.util.Arrays;

/**
 * PostgreSQL text[] 与 Java String[] 类型转换器
 */
@MappedTypes(String[].class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class StringArrayTypeHandler extends BaseTypeHandler<String[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String[] parameter, JdbcType jdbcType) throws SQLException {
        Connection conn = ps.getConnection();
        Array array = conn.createArrayOf("text", parameter);
        ps.setArray(i, array);
    }

    @Override
    public String[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toArray(rs.getArray(columnName));
    }

    @Override
    public String[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toArray(rs.getArray(columnIndex));
    }

    @Override
    public String[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toArray(cs.getArray(columnIndex));
    }

    private String[] toArray(Array array) throws SQLException {
        if (array == null) return new String[0];
        Object[] arr = (Object[]) array.getArray();
        return Arrays.copyOf(arr, arr.length, String[].class);
    }
}
