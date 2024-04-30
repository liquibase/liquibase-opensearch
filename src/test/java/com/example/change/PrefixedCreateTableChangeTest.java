package com.example.change;

import liquibase.statement.core.CreateTableStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrefixedCreateTableChangeTest {

    private PrefixedCreateTableChange prefixedCreateTableChange;

    @BeforeEach
    void setUp() {
        prefixedCreateTableChange = new PrefixedCreateTableChange();
    }

    @Test
    void getPrefix_returnsNull_whenNotSet() {
        assertNull(prefixedCreateTableChange.getPrefix());
    }

    @Test
    void getPrefix_returnsPrefix_whenSet() {
        String expectedPrefix = "testPrefix";
        prefixedCreateTableChange.setPrefix(expectedPrefix);

        assertEquals(expectedPrefix, prefixedCreateTableChange.getPrefix());
    }

    @Test
    void generateCreateTableStatement_usesStandardPrefix_whenPrefixNotSet() {
        prefixedCreateTableChange.setTableName("testTable");

        CreateTableStatement statement = prefixedCreateTableChange.generateCreateTableStatement();

        assertTrue(statement.getTableName().startsWith("standard_"));
    }

    @Test
    void generateCreateTableStatement_usesSetPrefix_whenPrefixSet() {
        String expectedPrefix = "testPrefix";
        prefixedCreateTableChange.setPrefix(expectedPrefix);
        prefixedCreateTableChange.setTableName("testTable");

        CreateTableStatement statement = prefixedCreateTableChange.generateCreateTableStatement();

        assertTrue(statement.getTableName().startsWith(expectedPrefix + "_"));
    }
}
