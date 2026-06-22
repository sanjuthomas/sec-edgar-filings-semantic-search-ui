package com.sanjuthomas.search.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Date;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

final class ResultSetProxy {

    private ResultSetProxy() {
    }

    static ResultSet of(Map<String, Object> columns) {
        Map<String, Object> values = new HashMap<>(columns);
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("getString".equals(name) && args.length == 1) {
                Object value = values.get(args[0]);
                return value == null ? null : value.toString();
            }
            if ("getLong".equals(name) && args.length == 1) {
                return toNumber(values.get(args[0])).longValue();
            }
            if ("getInt".equals(name) && args.length == 1) {
                return toNumber(values.get(args[0])).intValue();
            }
            if ("getDouble".equals(name) && args.length == 1) {
                return toNumber(values.get(args[0])).doubleValue();
            }
            if ("getDate".equals(name) && args.length == 1) {
                Object value = values.get(args[0]);
                if (value instanceof Date date) {
                    return date;
                }
                if (value instanceof java.time.LocalDate localDate) {
                    return Date.valueOf(localDate);
                }
                return null;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == double.class) {
                return 0.0d;
            }
            if (returnType == float.class) {
                return 0.0f;
            }
            return null;
        };
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[] {ResultSet.class},
                handler
        );
    }

    private static Number toNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value == null) {
            return 0;
        }
        return Double.parseDouble(value.toString());
    }
}
