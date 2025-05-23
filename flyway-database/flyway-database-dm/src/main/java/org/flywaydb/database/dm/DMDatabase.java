/*-
 * ========================LICENSE_START=================================
 * flyway-database-dm
 * ========================================================================
 * Copyright (C) 2010 - 2025 Red Gate Software Ltd
 * ========================================================================
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
 * =========================LICENSE_END==================================
 */
package org.flywaydb.database.dm;

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.database.base.Table;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.flywaydb.core.internal.jdbc.RowMapper;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DMDatabase extends Database<DMConnection> {
    public DMDatabase(Configuration configuration, JdbcConnectionFactory jdbcConnectionFactory, StatementInterceptor statementInterceptor) {
        super(configuration, jdbcConnectionFactory, statementInterceptor);
    }

    @Override
    protected DMConnection doGetConnection(Connection connection) {
        return new DMConnection(this, connection);
    }

    @Override
    public void ensureSupported(Configuration configuration) {

    }

    @Override
    public boolean supportsDdlTransactions() {
        return false;
    }

    @Override
    public String getBooleanTrue() {
        return "1";
    }

    @Override
    public String getBooleanFalse() {
        return "0";
    }

    @Override
    public boolean catalogIsSchema() {
        return false;
    }

    @Override
    public String getRawCreateScript(Table table, boolean baseline) {
        String tablespace = configuration.getTablespace() == null
                ? ""
                : " TABLESPACE \"" + configuration.getTablespace() + "\"";

        return "CREATE TABLE " + table + " (\n" +
                "    \"installed_rank\" INT NOT NULL,\n" +
                "    \"version\" VARCHAR2(50),\n" +
                "    \"description\" VARCHAR2(200) NOT NULL,\n" +
                "    \"type\" VARCHAR2(20) NOT NULL,\n" +
                "    \"script\" VARCHAR2(1000) NOT NULL,\n" +
                "    \"checksum\" INT,\n" +
                "    \"installed_by\" VARCHAR2(100) NOT NULL,\n" +
                "    \"installed_on\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,\n" +
                "    \"execution_time\" INT NOT NULL,\n" +
                "    \"success\" NUMBER(1) NOT NULL,\n" +
                "    CONSTRAINT \"" + table.getName() + "_pk\" PRIMARY KEY (\"installed_rank\")\n" +
                ")" + tablespace + ";\n" +
                (baseline ? getBaselineStatement(table) + ";\n" : "") +
                "CREATE INDEX \"" + table.getSchema().getName() + "\".\"" + table.getName() + "_s_idx\" ON " + table + " (\"success\");\n";
    }

    boolean queryReturnsRows(String query, String... params) throws SQLException {
        return getMainConnection().getJdbcTemplate().queryForBoolean("SELECT CASE WHEN EXISTS(" + query + ") THEN 1 ELSE 0 END FROM DUAL", params);
    }
    Set<String> getSystemSchemas() throws SQLException {

        // The list of known default system schemas
        Set<String> result = Stream.of(
                // Standard system accounts
                "SYS", "SYSTEM", "SYSDBA","SYSAUDITOR",
                // Auxiliary system accounts
                "SYSBACKUP", "SYSDG", "SYSKM", "SYSRAC", "SYS$UMF",
                // Enterprise Manager accounts
                "DBSNMP", "MGMT_VIEW", "SYSMAN",
                // Stored outlines
                "OUTLN",
                // Unified auditing
                "AUDSYS",
                // DM Configuration Manager
                "DM_OCM",
                // DM Database QoS Management
                "APPQOSSYS",
                // DM JavaVM
                "OJVMSYS",
                // DM Database Vault
                "DVF", "DVSYS",
                // Database Service Firewall
                "DBSFWUSER",
                // Remote scheduler agent
                "REMOTE_SCHEDULER_AGENT",
                // DM Directory Integration Platform
                "DIP",
                /*"APEX_######", "FLOWS_######",*/ // DM Application Express
                "APEX_PUBLIC_USER", "FLOWS_FILES",
                // DM XML Database
                "ANONYMOUS", "XDB", "XS$NULL",
                // DM Text
                "CTXSYS",
                // DM Label Security
                "LBACSYS",
                // DM Rules Manager and Expression Filter
                "EXFSYS",
                // DM Locator and Spatial
                "MDDATA", "MDSYS", "SPATIAL_CSW_ADMIN_USR", "SPATIAL_WFS_ADMIN_USR",
                // DM Multimedia
                "ORDDATA", "ORDPLUGINS", "ORDSYS", "SI_INFORMTN_SCHEMA",
                // DM Workspace Manager
                "WMSYS",
                // DM OLAP catalogs
                "OLAPSYS",
                // DM Warehouse Builder
                "OWBSYS", "OWBSYS_AUDIT",
                // Global Data Services
                "GSMADMIN_INTERNAL", "GSMCATUSER", "GSMUSER",
                // DM GoldenGate
                "GGSYS",
                // DM Ultra Search
                "WK_TEST", "WKSYS", "WKPROXY",
                // DM Data Mining
                "ODM", "ODM_MTR", "DMSYS",
                // Transparent Session Migration
                "TSMSYS"
        ).collect(Collectors.toSet());

//        result.addAll(getMainConnection().getJdbcTemplate().queryForStringList("SELECT USERNAME FROM ALL_USERS " +
//                "WHERE REGEXP_LIKE(USERNAME, '^(APEX|FLOWS)_\\d+$')" +
//                " OR DM_MAINTAINED = 'Y'"
//        ));
        return result;
    }
    /**
     * Checks whether XDB component is available or not.
     *
     * @return {@code true} if it is available, {@code false} if not.
     * @throws SQLException when checking availability of the component failed.
     */
    boolean isXmlDbAvailable() throws SQLException {
        return isDataDictViewAccessible("ALL_XML_TABLES");
    }
    /**
     * Checks whether Oracle Locator component is available or not.
     *
     * @return {@code true} if it is available, {@code false} if not.
     * @throws SQLException when checking availability of the component failed.
     */
    boolean isLocatorAvailable() throws SQLException {
        return isDataDictViewAccessible("MDSYS", "ALL_SDO_GEOM_METADATA");
    }
    /**
     * Checks whether the specified data dictionary view in the specified system schema is accessible (directly or
     * through a role) or not.
     *
     * @param owner the schema name, unquoted case-sensitive.
     * @param name the data dictionary view name to check, unquoted case-sensitive.
     * @return {@code true} if it is accessible, {@code false} if not.
     * @throws SQLException if the check failed.
     */
    private boolean isDataDictViewAccessible(String owner, String name) throws SQLException {
        return queryReturnsRows("SELECT * FROM ALL_TAB_PRIVS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?" +
                " AND PRIVILEGE = 'SELECT'", owner, name);
    }
    /**
     * Checks whether the specified SYS view is accessible (directly or through a role) or not.
     *
     * @param name the data dictionary view name to check, unquoted case-sensitive.
     * @return {@code true} if it is accessible, {@code false} if not.
     * @throws SQLException if the check failed.
     */
    boolean isDataDictViewAccessible(String name) throws SQLException {
        return isDataDictViewAccessible("SYS", name);
    }
    /**
     * Checks whether Flashback Data Archive option is available or not.
     *
     * @return {@code true} if it is available, {@code false} if not.
     * @throws SQLException when checking availability of the feature failed.
     */
    boolean isFlashbackDataArchiveAvailable() throws SQLException {
        return getAvailableOptions().contains("Flashback Data Archive");
    }
    /**
     * Returns the set of Oracle options available on the target database.
     *
     * @return the set of option titles.
     * @throws SQLException if retrieving of options failed.
     */
    private Set<String> getAvailableOptions() throws SQLException {
        return new HashSet<>(getMainConnection().getJdbcTemplate()
                .queryForStringList("SELECT PARAMETER FROM V$OPTION WHERE VALUE = 'TRUE'"));
    }
    /**
     * Returns the specified data dictionary view name prefixed with DBA_ or ALL_ depending on its accessibility.
     *
     * @param baseName the data dictionary view base name, unquoted case-sensitive, e.g. OBJECTS, TABLES.
     * @return the full name of the view with the proper prefix.
     * @throws SQLException if the check failed.
     */
    String dbaOrAll(String baseName) throws SQLException {
        return isPrivOrRoleGranted("SELECT ANY DICTIONARY") || isDataDictViewAccessible("DBA_" + baseName)
                ? "DBA_" + baseName
                : "ALL_" + baseName;
    }
    /**
     * Checks whether the specified privilege or role is granted to the current user.
     *
     * @return {@code true} if it is granted, {@code false} if not.
     * @throws SQLException if the check failed.
     */
    boolean isPrivOrRoleGranted(String name) throws SQLException {
        return queryReturnsRows("SELECT 1 FROM SESSION_PRIVS WHERE PRIVILEGE = ? UNION ALL " +
                "SELECT 1 FROM SESSION_ROLES WHERE ROLE = ?", name, name);
    }
    /**
     * Checks whether Data Mining option is available or not.
     *
     * @return {@code true} if it is available, {@code false} if not.
     * @throws SQLException when checking availability of the feature failed.
     */
    boolean isDataMiningAvailable() throws SQLException {
        return getAvailableOptions().contains("Data Mining");
    }
    @Override
    protected String doGetCurrentUser() throws SQLException {
        return getMainConnection().getJdbcTemplate().queryForString("SELECT USER FROM DUAL");
    }
}
