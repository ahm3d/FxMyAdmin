package com.javamyadmin.db;

public record ConnectionInfo(String host, int port, String user, String password) {

    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/"
                + "?useUnicode=true&characterEncoding=UTF-8"
                + "&allowMultiQueries=true"
                + "&allowPublicKeyRetrieval=true"
                + "&zeroDateTimeBehavior=CONVERT_TO_NULL"
                + "&connectTimeout=10000";
    }

    public String label() {
        return user + "@" + host + ":" + port;
    }
}
