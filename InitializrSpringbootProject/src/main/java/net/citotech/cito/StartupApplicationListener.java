/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.System.Logger;
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
        
        
        updateDb();
        
    }
    
    @Value("classpath:dbchanges/*.xml")
    private Resource[] resources;
    
    public String updateDb() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        URL url = loader.getResource(Common.CLASS_PATH_GENERAL_DBCHANGES_DIR);
        //String dir = new ClassPathResource(Common.CLASS_PATH_GENERAL_DBCHANGES_DIR).getPath();
        try {
            InputStream resource;
            for (final Resource res : resources) {
                //if (!res.getFilename().equals(".") || !res.getFilename().equals("..")) {
                    String file = Common.CLASS_PATH_GENERAL_DBCHANGES_DIR+File.separator+res.getFilename();
                    resource = new ClassPathResource(file).getInputStream();
                    String xml_data = StreamUtils.copyToString(resource, Charset.defaultCharset());
                    applyQueries(xml_data);
                //}
            }
            return "Done";
        } catch (IOException ex) {
            //ex.printStackTrace();
            java.util.logging.Logger.getLogger(TransactionsLogController.class.getName())
                            .log(Level.INFO, "Executed Rollback "+ex.getStackTrace(), ex);
            return ex.getMessage();
        }
    }
    
    
    private boolean applyQueries(String xml_data) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
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
                        + "WHERE query_id='"+nQuery.getQuery_id()+"'";
                    RowMapper rm = new RowMapper<QueryUpdate>() {
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
                            new MapSqlParameterSource(), 
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
                        ex.printStackTrace();
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
