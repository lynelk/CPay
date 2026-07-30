package net.citotech.cito;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import net.citotech.cito.Model.QueryUpdate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
/**
 *
 * @author josephtabajjwa
 */
@Component
public class StartupApplicationListener {
    public static int counter;
    
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;
    
    @Autowired
    TransactionTemplate transactionTemplate;
    
    
    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {
        counter++;
        
        java.util.logging.Logger.getLogger(TransactionsLogController.class.getName())
            .log(Level.INFO, "Database Applied "+counter, "");
        
        
        //updateDb();
        //validateGatewayConfig();

    }
    
    private void validateGatewayConfig() {
        try {
            net.citotech.cito.Model.Setting state = Common.getSettings("application_settings_state", jdbcTemplate);
            if (state == null || !"production".equalsIgnoreCase(state.getSetting_value().trim())) {
                return;
            }
            String[] requiredKeys = {
                "gw_mtn_api_url",
                "gw_airtelmoney_api_url",
                "gw_safaricom_api_url"
            };
            for (String key : requiredKeys) {
                net.citotech.cito.Model.Setting s = Common.getSettings(key, jdbcTemplate);
                if (s == null || s.getSetting_value().trim().isEmpty()) {
                    java.util.logging.Logger.getLogger(TransactionsLogController.class.getName())
                        .log(Level.SEVERE, "STARTUP CONFIG ERROR: setting '"+key+"' is required in production but is blank or missing.", "");
                }
            }
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(TransactionsLogController.class.getName())
                .log(Level.WARNING, "Could not validate gateway config at startup: "+ex.getMessage(), ex);
        }
    }

    @Value("classpath:dbchanges/*.xml")
    private Resource[] resources = new Resource[0];

    @Value("${cpay.legacy-dbchanges.enabled:false}")
    private boolean legacyDbChangesEnabled;

    @Value("${cpay.legacy-dbchanges.lock-timeout-seconds:30}")
    private int legacyDbChangesLockTimeoutSeconds;
    
    public String updateDb() {
        boolean lockHeld = false;
        try {
            if (!legacyDbChangesEnabled) {
                java.util.logging.Logger.getLogger(TransactionsLogController.class.getName())
                    .log(Level.INFO, "Legacy DB change XML runner disabled; Flyway is the canonical migration path.");
                return "Disabled";
            }
            lockHeld = acquireStartupMigrationLock();
            if (!lockHeld) {
                java.util.logging.Logger.getLogger(TransactionsLogController.class.getName())
                    .log(Level.WARNING, "Legacy DB change XML runner skipped; another instance holds the startup migration lock.");
                return "Locked";
            }
            if (resources == null || resources.length == 0) {
                java.util.logging.Logger.getLogger(TransactionsLogController.class.getName())
                    .log(Level.INFO, "No DB change XML resources found; skipping startup DB application.");
                return "Skipped";
            }
            InputStream resource;
            for (final Resource res : resources) {
                String file = Common.CLASS_PATH_GENERAL_DBCHANGES_DIR + File.separator + res.getFilename();
                resource = new ClassPathResource(file).getInputStream();
                String xml_data = StreamUtils.copyToString(resource, Charset.defaultCharset());
                applyQueries(xml_data);
            }
            return "Done";
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(TransactionsLogController.class.getName())
                            .log(Level.INFO, "Executed Rollback " + ex.getStackTrace(), ex);
            return ex.getMessage();
        } finally {
            if (lockHeld) {
                releaseStartupMigrationLock();
            }
        }
    }

    private boolean acquireStartupMigrationLock() {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("lock_name", "cpay:legacy-dbchanges");
        parameters.addValue("timeout_seconds", Math.max(0, legacyDbChangesLockTimeoutSeconds));
        Number locked = jdbcTemplate.queryForObject(
            "SELECT GET_LOCK(:lock_name, :timeout_seconds)",
            parameters,
            Number.class);
        return locked != null && locked.intValue() == 1;
    }

    private void releaseStartupMigrationLock() {
        try {
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("lock_name", "cpay:legacy-dbchanges");
            jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(:lock_name)", parameters, Number.class);
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(StartupApplicationListener.class.getName())
                .log(Level.WARNING, "Failed to release legacy DB changes startup lock: " + ex.getMessage(), ex);
        }
    }
    
    
    private boolean applyQueries(String xml_data) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbFactory.setXIncludeAware(false);
            dbFactory.setExpandEntityReferences(false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new InputSource(new StringReader(xml_data)));
            doc.getDocumentElement().normalize();
            NodeList nList = doc.getElementsByTagName("Query");
            ArrayList<QueryUpdate> queries = new ArrayList<>();

            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    String sql = "";
                    String rollback = "";
                    String query_id = eElement.getAttribute("id");

                    if (eElement.getElementsByTagName("Sql").getLength() > 0) {
                        sql = eElement
                          .getElementsByTagName("Sql")
                          .item(0)
                          .getTextContent().trim();
                    }
                    if (eElement.getElementsByTagName("RollBack").getLength() > 0) {
                        rollback = eElement
                          .getElementsByTagName("RollBack")
                          .item(0)
                          .getTextContent().trim();
                    }

                    if (!sql.isEmpty()) {
                        QueryUpdate nQuery = new QueryUpdate();
                        nQuery.setQuery_id(query_id);
                        nQuery.setSql(sql);
                        nQuery.setRollback(rollback);
                        queries.add(nQuery);
                        //jdbcTemplate.execute(sql);
                    }
                }
            }
            //Now apply queries
            //Insert query into db_changes table
            String sqlQueryInsert = "INSERT INTO "+Common.DB_TABLE_DB_CHANGES+" "
            +" SET `query_id`=:query_id,"
            +" `sql_text`=:sql_text,"
            +" `roll_back`=:roll_back";

            for (int i=0; i < queries.size(); i++) {
                QueryUpdate nQuery = queries.get(i);
                //Select to see if this query was executed.
                if (nQuery.getQuery_id() != null && !nQuery.getQuery_id().isEmpty()) {
                    String checkSql = "SELECT * FROM `"+Common.DB_TABLE_DB_CHANGES+"` "
                        + "WHERE query_id=:query_id";
                    MapSqlParameterSource checkParams = new MapSqlParameterSource();
                    checkParams.addValue("query_id", nQuery.getQuery_id());
                    RowMapper<QueryUpdate> rm = new RowMapper<QueryUpdate>() {
                    public QueryUpdate mapRow(ResultSet rs, int rowNum) throws SQLException {
                            QueryUpdate t = new QueryUpdate();
                            t.setId(BigInteger.valueOf(rs.getLong("id")));
                            t.setQuery_id(rs.getString("query_id"));
                            t.setSql(rs.getString("sql_text"));
                            t.setRollback(rs.getString("roll_back"));
                            t.setCreated_on(rs.getString("created_on"));
                            return t;
                        }
                    };
                    List<QueryUpdate> listQueries = jdbcTemplate.query(checkSql, 
                            checkParams,
                            rm);
                    if (listQueries.size()>0) {
                        java.util.logging.Logger.getLogger(TransactionsLogController.class.getName())
                                        .log(Level.INFO, "Query already applied: "+nQuery.getQuery_id(), "");
                        continue;
                    }
                    //Now go ahead and apply this query.
                    try {
                        PreparedStatementCallback<Boolean> action = new PreparedStatementCallback<Boolean>() {
                            @Override
                            public Boolean doInPreparedStatement(PreparedStatement ps)
                                            throws SQLException, DataAccessException {
                                    //ps.setString(1, nQuery.getSql());
                                    return ps.execute();
                            }
                        };

                        Boolean q = jdbcTemplate.execute(nQuery.getSql(), action);
                        //Now add the statement to this database table.
                        if (q) {
                            java.util.logging.Logger.getLogger(TransactionsLogController.class.getName())
                                        .log(Level.INFO, "Executed SQL "+nQuery.getSql(), "");
                        }

                        //Now save this query;
                        MapSqlParameterSource parameters = new MapSqlParameterSource();
                        parameters.addValue("query_id", nQuery.getQuery_id());
                        parameters.addValue("sql_text", nQuery.getSql());
                        parameters.addValue("roll_back", nQuery.getRollback());

                        KeyHolder keyHolder = new GeneratedKeyHolder();

                        jdbcTemplate.update(sqlQueryInsert, parameters, keyHolder);

                        BigInteger qId = (BigInteger)keyHolder.getKey();
                        nQuery.setId(qId);
                        queries.set(i, nQuery);

                    } catch (Exception ex) {
                        Logger.getLogger(StartupApplicationListener.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                        for (int j=i; j >= 0; j--) {
                            //Ignore the current failed query
                            if (j==i) {
                                continue;
                            }
                            QueryUpdate nQueryR = queries.get(j);
                            PreparedStatementCallback<Boolean> action = new PreparedStatementCallback<Boolean>() {
                                @Override
                                public Boolean doInPreparedStatement(PreparedStatement ps)
                                                throws SQLException, DataAccessException {

                                        return ps.execute();
                                }
                            };

                            //Remove this query from the db
                            String removeQuery = "DELETE FROM `"+Common.DB_TABLE_DB_CHANGES+"` "
                                    + " WHERE query_id =:query_id ";
                            MapSqlParameterSource parametersD = new MapSqlParameterSource();
                            parametersD.addValue("query_id", nQueryR.getQuery_id());
                            jdbcTemplate.update(removeQuery, parametersD);

                            //Execute the rollback statement
                            Boolean q = jdbcTemplate.execute(nQueryR.getRollback(), action);
                            if (q) {
                                java.util.logging.Logger.getLogger(TransactionsLogController.class.getName())
                                        .log(Level.INFO, "Executed Rollback "+nQueryR.getRollback(), ex);
                            }
                        }
                        return false;
                    }
                }
            }

        } catch (Exception ex) {

            java.util.logging.Logger.getLogger(TransactionsLogController.class.getName())
                        .log(Level.INFO, "Executed Rollback "+ex.getStackTrace(), ex);
            return false;
        }
        return true;
    }
}

