package net.flectone.bot.util.creator;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.flectone.bot.config.Config;
import net.flectone.bot.processing.SystemVariableResolver;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class BackupCreator {

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");

    private final SystemVariableResolver systemVariableResolver;
    private final @Named("projectPath") Path projectPath;
    private final @Named("backupPath") Path backupPath;
    private final Logger logger;

    @Setter
    @Nullable
    private String preInitVersion;

    public void backup(Path pathToFile) {
        if (preInitVersion == null) {
            logger.warn("Backup is not needed if the version has not changed");
            return;
        }

        String fileName = pathToFile.getFileName().toString();
        backup(fileName, pathToFile);
    }

    public void backup(Config.Database database) {
        if (preInitVersion == null) {
            logger.warn("Backup is not needed if the version has not changed");
            return;
        }

        Config.Database.Type databaseType = database.type();
        String databaseName = systemVariableResolver.substituteEnvVars(database.name());
        switch (databaseType) {
            case SQLITE, H2 -> {
                databaseName = databaseName + (databaseType == Config.Database.Type.SQLITE ? ".db" : ".h2.mv.db");

                backup(databaseName, projectPath.resolve(databaseName));
            }
            case MYSQL, MARIADB, POSTGRESQL -> {
                try {
                    String backupFileName = databaseName + "_" + SIMPLE_DATE_FORMAT.format(new Date()) + ".sql";
                    Path backupPath = resolveBackupPath(backupFileName);

                    String host = systemVariableResolver.substituteEnvVars(database.host());
                    String port = systemVariableResolver.substituteEnvVars(database.port());
                    String user = systemVariableResolver.substituteEnvVars(database.user());
                    String password = systemVariableResolver.substituteEnvVars(database.password());

                    Map<String, String> env = new HashMap<>(System.getenv());
                    ProcessBuilder processBuilder;
                    if (databaseType == Config.Database.Type.MYSQL || databaseType == Config.Database.Type.MARIADB) {
                        env.put("MYSQL_PWD", password);
                        processBuilder = new ProcessBuilder(
                                getMySQLDumpCommand(),
                                "-h", host,
                                "-P", port,
                                "-u", user,
                                "--ssl=0",
                                "--ssl-verify-server-cert=0",
                                "--single-transaction",
                                "--routines",
                                "--triggers",
                                "--events",
                                databaseName
                        );
                    } else {
                        env.put("PGPASSWORD", password);
                        processBuilder = new ProcessBuilder(
                                "pg_dump",
                                "-h", host,
                                "-p", port,
                                "-U", user,
                                "-d", databaseName,
                                "-w"
                        );
                    }

                    processBuilder.redirectOutput(backupPath.toFile());
                    processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
                    processBuilder.environment().putAll(env);

                    Process process = processBuilder.start();

                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        logger.warn("{} backup failed (exit code: {})", databaseType, exitCode);
                    }
                } catch (IOException | InterruptedException e) {
                    logger.warn("Failed to backup {}: {}", databaseType, e.getMessage());
                }
            }
        }
    }

    private String getMySQLDumpCommand() {
        if (isCommandAvailable("mariadb-dump")) {
            return "mariadb-dump";
        }

        return "mysqldump";
    }

    private boolean isCommandAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Path resolveBackupPath(String newFileName) {
        return backupPath.resolve(preInitVersion).resolve(newFileName);
    }

    private void backup(String fileName, Path pathToFile) {
        String newFileName = fileName + "_" + SIMPLE_DATE_FORMAT.format(new Date());
        Path backupFilePath = resolveBackupPath(newFileName);

        try {
            Files.createDirectories(backupFilePath.getParent());
            Files.copy(pathToFile, backupFilePath);
        } catch (IOException e) {
            logger.warn("Failed to backup {}", fileName, e);
        }
    }
}
