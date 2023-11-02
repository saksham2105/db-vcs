package sdk.db.vcs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import sdk.db.vcs.annotations.EnableDbVcs;
import sdk.db.vcs.exceptions.DbVcsException;
import sdk.db.vcs.model.DBVcsSchema;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Component
public class DbVcsApplicationEventListener  implements ApplicationListener<ContextRefreshedEvent> {

    @Qualifier("dataSource")
    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    private static final String DB_VCS_SCHEMA = "db_vcs_schema";

    private static final Logger logger = Logger.getLogger(DbVcsApplicationEventListener.class.getName());
    private static final String createTableSQL = "CREATE TABLE " + DB_VCS_SCHEMA + " ("
            + "version_no varchar(255) PRIMARY KEY,"
            + "executed_at varchar(100) NOT NULL,"
            + "file_hash varchar(100) NOT NULL,"
            + "sql_file varchar(100) NOT NULL"
            + ")";

    private static String dbVcsResourceFolder = "db-migration";
    private static final String dbVcsMigrationLocation = "db.vcs.migration.location";


    private MessageDigest digest;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        Map<String,Object> beans = context.getBeansWithAnnotation(Configuration.class);
        boolean featureEnabled = false;
        for (Map.Entry entry : beans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> originalClass = getOriginalClass(bean);
            if (originalClass.isAnnotationPresent(EnableDbVcs.class)) {
                featureEnabled = true;
                break;
            }
        }
        featureEnabled = featureEnabled || (environment.getProperty("db.vcs.enabled") != null && environment.getProperty("db.vcs.enabled").equals("true"));
        if (!featureEnabled) {
            logger.info("DbVcs Feature is Disabled");
            return;
        }
        if (environment.getProperty(dbVcsMigrationLocation) != null) {
            dbVcsResourceFolder = environment.getProperty(dbVcsMigrationLocation);
        }
        ClassLoader cl = this.getClass().getClassLoader();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
        try (Connection connection = dataSource.getConnection()) {
            digest = MessageDigest.getInstance("MD5");
            connection.setAutoCommit(true);
            PreparedStatement preparedStatement;
            List<Resource> resources = Arrays.asList(resolver.getResources(String.format("classpath*:/%s/*.sql", dbVcsResourceFolder)));

            resources = resources.stream().filter(this::isValidResourceFile).sorted((o1, o2) -> {
                Integer version1 = Integer.valueOf(o1.getFilename().split("__")[0].substring(1));
                Integer version2 = Integer.valueOf(o2.getFilename().split("__")[0].substring(1));
                return version1.compareTo(version2);
            }).collect(Collectors.toList());

            if (!resources.isEmpty()) {
                createBaseSchema(connection);
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            for (Resource resource : resources) {
                File file = resource.getFile();
                String currentVersion = file.getName().split("__")[0];
                String lastExecutedVersion = getLastExecutedVersion(connection);
                if (lastExecutedVersion != null && !isVersionExecuted(connection, currentVersion)) {
                    Integer lastExecutedVersionNo = Integer.parseInt(lastExecutedVersion.substring(1));
                    Integer currentExecutedVersionNo = Integer.parseInt(currentVersion.substring(1));
                    if (currentExecutedVersionNo <= lastExecutedVersionNo) {
                        throw new DbVcsException("Can't execute as current version " + currentVersion + " can't be less than or equals to executed version : " + lastExecutedVersion);
                    }
                }
                String executedAt = dateFormat.format(new java.util.Date());
                FileReader fileReader = new FileReader(file);
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(fileReader) ) {
                    int character;
                    while ((character = reader.read()) != -1) {
                        content.append((char) character);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                String fileContent = content.toString().trim();
                digest.update(fileContent.getBytes());
                byte[] md5HashBytes = digest.digest();
                String fileHash = getHexString(md5HashBytes);
                DBVcsSchema dbVcsSchema = getDbVcsSchema(connection, currentVersion);
                String[] queries = fileContent.split(";");
                if (dbVcsSchema == null) {
                    insertInDbVcsSchema(connection, currentVersion, executedAt, fileHash, file.getName());
                } else {
                    if (!fileHash.equals(dbVcsSchema.getFileHash())) {
                        throw new DbVcsException("Invalid File Hash for : " + file.getName());
                    }
                    continue;
                }
                for (String query : queries) {
                    query = query.replaceAll("\n", " ");
                    if (query != null && !query.isEmpty()) {
                        preparedStatement = connection.prepareStatement(query);
                        preparedStatement.execute();
                    }
                }
                logger.info("Successfully executed Script : " + file.getName());
            }
        } catch (Exception e) {
            throw new DbVcsException(e.getMessage());
        }

    }

    private boolean isValidResourceFile(Resource resource) {
        String fileName = resource.getFilename();
        if (fileName.charAt(0) != 'V') return false;
        String[] fileSegments = fileName.split("__");
        if (fileSegments.length == 0) {
            return false;
        }
        try {
            Integer version = Integer.valueOf(fileSegments[0].substring(1));
            return version > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String getLastExecutedVersion(Connection connection) {
        try {
            String sql = "SELECT version_no FROM " + DB_VCS_SCHEMA + " ORDER BY version_no DESC LIMIT 1";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String lastVersionExecuted = resultSet.getString("version_no");
                return lastVersionExecuted;
            } else {
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean isVersionExecuted(Connection connection,
                                      String versionNo) {
        return getDbVcsSchema(connection, versionNo) != null;
    }

    private DBVcsSchema getDbVcsSchema(Connection connection,
                                       String versionNo) {
        try {
            String sql = "SELECT * FROM " + DB_VCS_SCHEMA + " WHERE version_no = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, versionNo);

            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String executedAt = resultSet.getString("executed_at");
                String fileHash = resultSet.getString("file_hash");
                String sqlFile = resultSet.getString("sql_file");
                return new DBVcsSchema(versionNo, executedAt, fileHash, sqlFile);
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void insertInDbVcsSchema(Connection connection, String versionNo,
                                     String executedAt, String fileHash, String sqlFile) {
        try {
            String insertSQL = "INSERT INTO " + DB_VCS_SCHEMA + " (version_no, executed_at, file_hash, sql_file) VALUES (?, ?, ?, ?)";
            PreparedStatement statement = connection.prepareStatement(insertSQL);
            statement.setString(1, versionNo);
            statement.setString(2, executedAt);
            statement.setString(3, fileHash);
            statement.setString(4, sqlFile);
            statement.execute();
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new DbVcsException(exception.getMessage());
        }
    }

    private String getHexString(byte[] md5HashBytes) {
        // Convert the bytes to a hexadecimal string
        StringBuilder hexString = new StringBuilder();
        for (byte b : md5HashBytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private void createBaseSchema(Connection connection) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet tables = metaData.getTables(null, null, DB_VCS_SCHEMA, null);
            if (!tables.next()) {
                PreparedStatement statement = connection.prepareStatement(createTableSQL);
                statement.execute();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new DbVcsException(exception.getMessage());
        }
    }

    private Class<?> getOriginalClass(Object bean) {
        String className = bean.getClass().getName();
        String originalClassName = className;

        int dollarIndex = className.indexOf("$$");
        if (dollarIndex != -1) {
            originalClassName = className.substring(0, dollarIndex);
        }

        try {
            return Class.forName(originalClassName);
        } catch (ClassNotFoundException e) {
            return bean.getClass();
        }
    }
}
