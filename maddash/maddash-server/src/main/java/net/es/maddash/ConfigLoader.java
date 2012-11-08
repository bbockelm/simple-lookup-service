package net.es.maddash;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import net.es.maddash.checks.Check;
import net.es.maddash.checks.CheckConstants;
import net.sf.json.JSONObject;

import com.mchange.v2.c3p0.ComboPooledDataSource;

/**
 * Loads YAML configuration file into scheduler database. 
 * It will add new checks and disable checks that have been removed.
 * 
 * @author Andy Lake <andy@es.net>
 *
 */
public class ConfigLoader {
    static final Logger log = Logger.getLogger(ConfigLoader.class);

    //properties
    static final public String PROP_DIMENSIONS = "groupMembers";
    static final public String PROP_DIMENSIONS_ID = "id";
    static final public String PROP_DIMENSIONS_LABEL = "label";
    
    static final public String PROP_GROUPS = "groups";
    
    static final public String PROP_CHECKS = "checks";
    static final public String PROP_CHECKS_NAME = "name";
    static final public String PROP_CHECKS_DESCRIPTION = "description";
    static final public String PROP_CHECKS_TYPE = "type";
    static final public String PROP_CHECKS_PARAMS = "params";
    static final public String PROP_CHECKS_INTERVAL = "checkInterval";
    static final public String PROP_CHECKS_RETRY_INT = "retryInterval";
    static final public String PROP_CHECKS_RETRY_ATT = "retryAttempts";
    static final public String PROP_CHECKS_TIMEOUT = "timeout";

    static final public String PROP_GRIDS = "grids";
    static final public String PROP_GRIDS_NAME = "name";
    static final public String PROP_GRIDS_ROWS = "rows";
    static final public String PROP_GRIDS_COLS = "columns";
    static final public String PROP_GRIDS_CHECKS = "checks";
    static final public String PROP_GRIDS_EXCL_SELF = "excludeSelf";
    static final public String PROP_GRIDS_EXCL_CHECKS = "excludeChecks";
    static final public String PROP_GRIDS_COL_ALG = "columnAlgorithm";
    static final public String PROP_GRIDS_ROW_ORDER = "rowOrder";
    static final public String PROP_GRIDS_COL_ORDER = "colOrder";
    static final public String PROP_GRIDS_STATUS_LABELS = "statusLabels";
    static final public String PROP_GRIDS_STATUS_LABELS_OK = "ok";
    static final public String PROP_GRIDS_STATUS_LABELS_WARNING = "warning";
    static final public String PROP_GRIDS_STATUS_LABELS_CRITICAL = "critical";
    static final public String PROP_GRIDS_STATUS_LABELS_UNKNOWN = "unknown";
    static final public String PROP_GRIDS_STATUS_LABELS_NOTRUN = "notrun";

    static final public String ALG_AFTER = "afterself";
    static final public String ALG_BEFORE = "beforeself";
    static final public String ALG_ALL = "all";
    static final public String ORDER_ALPHA = "alphabetical";
    static final public String ORDER_GROUP = "group";
    static final public String EXCL_CHECKS_DEFAULT = "default";
    static final public String EXCL_CHECKS_ALL = "all";
    
    /**
     * Loads YAML properties into scheduler database
     * 
     * @param config Properties loaded from YAML file as a Map
     * @param dataSource the dataSource to use to access the database
     * @return a map of check Classes indexed by the class name
     * @throws ClassNotFoundException
     */
    static public Map<String,Class> load(Map config, ComboPooledDataSource dataSource) throws ClassNotFoundException {
        checkRequiredProp(config, PROP_CHECKS);
        checkRequiredProp(config, PROP_GROUPS);
        checkRequiredProp(config, PROP_GRIDS);
        Map<String, Map> checkMap = (Map<String, Map>) config.get(PROP_CHECKS);
        Map groupMap = (Map) config.get(PROP_GROUPS);
        List<Map> gridList = (List<Map>) config.get(PROP_GRIDS);
        HashMap<String, Class> checkTypeClassMap = new HashMap<String, Class>();

        Connection conn = null;
        try{
            conn = dataSource.getConnection();
            
            //populate the dimensions table
            conn.createStatement().executeUpdate("DELETE FROM dimensions");
            PreparedStatement insertDimension = conn.prepareStatement("INSERT INTO dimensions VALUES(DEFAULT, ?, ?, ?)");
            if(config.containsKey(PROP_DIMENSIONS) && config.get(PROP_DIMENSIONS) != null){
            	int i = 1;
            	for(Map<String,String> dimension : (List<Map<String,String>>) config.get(PROP_DIMENSIONS)){
            		if(!dimension.containsKey(PROP_DIMENSIONS_ID) || dimension.get(PROP_DIMENSIONS_ID) == null){
            			throw new RuntimeException("Found dimension at position " + i + 
            					" that is missing 'id' attribute");
            		}
            		for(String dimensionParam : dimension.keySet()){
            			if(dimensionParam.equals(PROP_DIMENSIONS_ID)){
            				continue;
            			}
            			insertDimension.setString(1, dimension.get(PROP_DIMENSIONS_ID));
            			insertDimension.setString(2, dimensionParam);
            			insertDimension.setString(3, dimension.get(dimensionParam));
            			insertDimension.executeUpdate();
            		}
            		i++;
            	}
            }
            
            //Build prepared statements for each check
            HashMap<String, Integer> templateIdMap = new HashMap<String, Integer>();
            PreparedStatement selTemplateStmt = conn.prepareStatement("SELECT id FROM " +
                    "checkTemplates WHERE checkType=? AND checkParams=? AND " +
            "checkInterval=? AND retryInterval=? AND retryAttempts=? AND timeout=?");
            PreparedStatement insertTemplateStmt = conn.prepareStatement("INSERT INTO " +
                    "checkTemplates VALUES(DEFAULT, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            for(String checkName : checkMap.keySet()){
                Map check = checkMap.get(checkName);
                checkRequiredProp(check, PROP_CHECKS_NAME);
                checkRequiredProp(check, PROP_CHECKS_DESCRIPTION);
                checkRequiredProp(check, PROP_CHECKS_TYPE);
                if(!checkTypeClassMap.containsKey(check.get(PROP_CHECKS_TYPE)+"")){
                    checkTypeClassMap.put(check.get(PROP_CHECKS_TYPE)+"", 
                            loadClass(check.get(PROP_CHECKS_TYPE)+""));
                }
                selTemplateStmt.setString(1, (String) check.get(PROP_CHECKS_TYPE));
                insertTemplateStmt.setString(1, (String) check.get(PROP_CHECKS_TYPE));

                String jsonParamString = CheckConstants.EMPTY_PARAMS;
                if(check.containsKey(PROP_CHECKS_PARAMS) && check.get(PROP_CHECKS_PARAMS) != null){
                    jsonParamString = JSONObject.fromObject(check.get(PROP_CHECKS_PARAMS)).toString();
                }
                selTemplateStmt.setString(2, jsonParamString);
                insertTemplateStmt.setString(2, jsonParamString);

                checkRequiredProp(check, PROP_CHECKS_INTERVAL);
                selTemplateStmt.setInt(3, (Integer) check.get(PROP_CHECKS_INTERVAL));
                insertTemplateStmt.setInt(3, (Integer) check.get(PROP_CHECKS_INTERVAL));

                checkRequiredProp(check, PROP_CHECKS_RETRY_INT);
                selTemplateStmt.setInt(4, (Integer) check.get(PROP_CHECKS_RETRY_INT));
                insertTemplateStmt.setInt(4, (Integer) check.get(PROP_CHECKS_RETRY_INT));

                checkRequiredProp(check, PROP_CHECKS_RETRY_ATT);
                selTemplateStmt.setInt(5, (Integer) check.get(PROP_CHECKS_RETRY_ATT));
                insertTemplateStmt.setInt(5, (Integer) check.get(PROP_CHECKS_RETRY_ATT));

                checkRequiredProp(check, PROP_CHECKS_TIMEOUT);
                selTemplateStmt.setInt(6, (Integer) check.get(PROP_CHECKS_TIMEOUT));
                insertTemplateStmt.setInt(6, (Integer) check.get(PROP_CHECKS_TIMEOUT));

                ResultSet selResult = selTemplateStmt.executeQuery();
                if(selResult.next()){
                    templateIdMap.put(checkName, selResult.getInt(1));
                }else{
                    insertTemplateStmt.executeUpdate();
                    ResultSet genKeys = insertTemplateStmt.getGeneratedKeys();
                    if(!genKeys.next()){
                        throw new RuntimeException("No generated keys");
                    }
                    templateIdMap.put(checkName, genKeys.getInt(1));
                }
            }

            //disable all checks
            conn.createStatement().executeUpdate("UPDATE checks SET active=0");

            //remove all grids
            //NOTE: Remove this if ever have foreign keys to this value
            conn.createStatement().executeUpdate("DELETE FROM grids");

            //parse grids and update database
            PreparedStatement insertGridStmt = conn.prepareStatement("INSERT INTO grids VALUES(DEFAULT, ?, ?, ?, ?, ?, ?)");            
            PreparedStatement selCheckStmt = conn.prepareStatement("SELECT id FROM checks WHERE " +
            "gridName=? AND rowName=? AND colName =? AND checkName=? AND checkTemplateId=?");
            PreparedStatement updateCheckStmt = conn.prepareStatement("UPDATE checks SET description=?, rowOrder=?, colOrder=?, active=1 WHERE id=?");
            PreparedStatement insertCheckStmt = conn.prepareStatement("INSERT INTO checks VALUES(DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, " + CheckConstants.RESULT_NOTRUN  + "," + CheckConstants.RESULT_NOTRUN  + ", 'Check has not run', 0, 1)");
            for(Map gridMap : gridList){
                String rowOrder = (String) gridMap.get(PROP_GRIDS_ROW_ORDER);
                String colOrder = (String) gridMap.get(PROP_GRIDS_COL_ORDER);

                checkRequiredProp(gridMap, PROP_GRIDS_NAME);
                checkRequiredProp(gridMap, PROP_GRIDS_ROWS);
                checkRequiredProp(gridMap, PROP_GRIDS_EXCL_SELF);
                checkRequiredProp(gridMap, PROP_GRIDS_COL_ALG);
                checkRequiredProp(gridMap, PROP_GRIDS_COLS);
                checkRequiredProp(gridMap, PROP_GRIDS_CHECKS);
                checkRequiredProp(gridMap, PROP_GRIDS_STATUS_LABELS);
                // checkRequiredProp(gridMap, PROP_GRIDS_ROW_ORDER);
                // checkRequiredProp(gridMap, PROP_GRIDS_COL_ORDER);

                String colAlg = ((String)gridMap.get(PROP_GRIDS_COL_ALG)).toLowerCase();
                int exclSelf = (Integer)gridMap.get(PROP_GRIDS_EXCL_SELF);
                Map<String, List<String>> exclChecks = new HashMap<String, List<String>>();
                if(gridMap.containsKey(PROP_GRIDS_EXCL_CHECKS) && 
                        gridMap.get(PROP_GRIDS_EXCL_CHECKS) != null){
                    exclChecks = (Map<String, List<String>>) gridMap.get(PROP_GRIDS_EXCL_CHECKS);
                }
                
                //check groups
                checkRequiredProp(groupMap, (String) gridMap.get(PROP_GRIDS_ROWS));
                checkRequiredProp(groupMap, (String) gridMap.get(PROP_GRIDS_COLS));

                //Load rows and columns and get in right order
                List<String> rows = new ArrayList<String>();
                for(String tmpRow : (List<String>) groupMap.get(gridMap.get(PROP_GRIDS_ROWS))){
                    rows.add(tmpRow);
                }
                if(ORDER_ALPHA.equals(rowOrder)){
                    Collections.sort(rows);
                }

                List<String> cols =  (List<String>) groupMap.get(gridMap.get(PROP_GRIDS_COLS));
                List<String> tmpCols = new ArrayList<String>();
                for(String tmpCol : cols){
                    tmpCols.add(tmpCol);
                }
                if(ORDER_ALPHA.equals(colOrder)){
                    Collections.sort(tmpCols);
                }
                HashMap<String,Integer> colOrderMap = new HashMap<String,Integer>();
                for(int i = 0; i < tmpCols.size(); i++){
                    colOrderMap.put(tmpCols.get(i), i);
                }

                //load up grids table
                Map<String,String> statusLabelMap = (Map<String, String>) gridMap.get(PROP_GRIDS_STATUS_LABELS);
                insertGridStmt.setString(1, (String)gridMap.get(PROP_GRIDS_NAME));
                insertGridStmt.setString(2, ConfigLoader.genStatusLabel(statusLabelMap, PROP_GRIDS_STATUS_LABELS_OK));
                insertGridStmt.setString(3, ConfigLoader.genStatusLabel(statusLabelMap, PROP_GRIDS_STATUS_LABELS_WARNING));
                insertGridStmt.setString(4, ConfigLoader.genStatusLabel(statusLabelMap, PROP_GRIDS_STATUS_LABELS_CRITICAL));
                insertGridStmt.setString(5, ConfigLoader.genStatusLabel(statusLabelMap, PROP_GRIDS_STATUS_LABELS_UNKNOWN));
                insertGridStmt.setString(6, ConfigLoader.genStatusLabel(statusLabelMap, PROP_GRIDS_STATUS_LABELS_NOTRUN));
                insertGridStmt.executeUpdate();

                //load up check database table
                selCheckStmt.setString(1, (String)gridMap.get(PROP_GRIDS_NAME));
                for(int ri = 0; ri < rows.size(); ri++){
                    String row = rows.get(ri);
                    boolean rowColFound = false;
                    selCheckStmt.setString(2, row);
                    for(String col : cols){
                        //compare column and row
                        if(col.equals(row) && exclSelf == 1){
                            rowColFound = true;
                            continue;
                        }else if(col.equals(row)){
                            rowColFound = true;
                        }
                        
                        //check if we should skip
                        if(exclChecks.containsKey(row) && exclChecks.get(row) != null){
                            if(exclChecks.get(row).contains(col) || 
                                    exclChecks.get(row).contains(EXCL_CHECKS_ALL)){
                               continue; 
                            }
                            
                        }else if(exclChecks.containsKey(EXCL_CHECKS_DEFAULT) && 
                                exclChecks.get(EXCL_CHECKS_DEFAULT) != null){
                            if(exclChecks.get(EXCL_CHECKS_DEFAULT).contains(col) || 
                                    exclChecks.get(EXCL_CHECKS_DEFAULT).contains(EXCL_CHECKS_ALL)){
                               continue; 
                            }
                        }

                        //determine if we need to add a check
                        if(colAlg.equals(ALG_AFTER) && !rowColFound){
                            continue;
                        }else if(colAlg.equals(ALG_BEFORE) && rowColFound){
                            break;
                        }

                        //Handle database insert/update
                        selCheckStmt.setString(3, col);
                        for(String checkName : (List<String>)gridMap.get(PROP_GRIDS_CHECKS)){
                            String checkNiceName = (String)checkMap.get(checkName).get(PROP_CHECKS_NAME);
                            String checkDescrName = (String)checkMap.get(checkName).get(PROP_CHECKS_DESCRIPTION);
                            checkRequiredProp(templateIdMap, checkName);
                            selCheckStmt.setString(4, checkNiceName);
                            selCheckStmt.setInt(5, templateIdMap.get(checkName));
                            ResultSet selResult = selCheckStmt.executeQuery();
                            if(selResult.next()){
                                log.debug("Updated check " + checkName);
                                updateCheckStmt.setString(1, formatCheckDescription(checkDescrName, row, col));
                                updateCheckStmt.setInt(2, ri);
                                updateCheckStmt.setInt(3, colOrderMap.get(col));
                                updateCheckStmt.setInt(4, selResult.getInt(1));
                                updateCheckStmt.executeUpdate();
                            }else{
                                log.debug("Added check " + checkName);
                                insertCheckStmt.setInt(1, templateIdMap.get(checkName));
                                insertCheckStmt.setString(2, (String)gridMap.get(PROP_GRIDS_NAME));
                                insertCheckStmt.setString(3, row);
                                insertCheckStmt.setString(4, col);
                                insertCheckStmt.setString(5, checkNiceName);
                                insertCheckStmt.setInt(6, ri);
                                insertCheckStmt.setInt(7, colOrderMap.get(col));
                                insertCheckStmt.setString(8, formatCheckDescription(checkDescrName, row, col));
                                insertCheckStmt.executeUpdate();
                            }

                        }
                    }
                }
            }
            conn.close();
        }catch(SQLException e){
            if(conn != null){
                try{
                    conn.close();
                }catch(SQLException e2){}
            }
            e.printStackTrace();
            throw new RuntimeException("Error loading database: " + e.getMessage());
        }

        return checkTypeClassMap;
    }

    private static String genStatusLabel(Map<String, String> statusLabelMap, String label) {
        if(!statusLabelMap.containsKey(label) || statusLabelMap.get(label) == null){
            return "";
        }
        return statusLabelMap.get(label);
    }

    private static String formatCheckDescription(String description, String rowName, String colName) {
        description = description.replaceAll("%row", rowName);
        description = description.replaceAll("%col", colName);
        return description;
    }

    private static Class loadClass(String className) throws ClassNotFoundException {
        ClassLoader classLoader = ConfigLoader.class.getClassLoader();
        Class checkClass = classLoader.loadClass(className);
        for(Class iface : checkClass.getInterfaces()){
            if(iface.getName().equals(Check.class.getName())){
                return checkClass;
            }
        }
        throw new RuntimeException("Class " + className + " does not implement Check interface");
    }

    static private void checkRequiredProp(Map config, String propName){
        if(!config.containsKey(propName) || config.get(propName) == null){
            throw new RuntimeException("The property '" + propName + "' is not defined");
        }
    }
}
