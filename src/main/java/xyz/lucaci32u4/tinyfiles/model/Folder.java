package xyz.lucaci32u4.tinyfiles.model;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Folder {
    private static final Logger logger = LoggerFactory.getLogger(Folder.class);
    public static final Pattern pathnameRegex = Pattern.compile("^(/(\\.?[\\w\\- ]+)+)+$");
    public static final Pattern filenameRegex = File.filenameRegex;

    private final String sysPrefix;
    private final Folder parent;
    private final String sysCompletePath;
    private final String completePath;
    private final String name;

    private final Object dataLock = new Object();
    private List<Folder> folders;
    private List<File> files;
    private long lastModifiedTimeUnixEpoch;
    private long size;

    public Folder(@NotNull Folder parent, @NotNull String name) {
        this.sysPrefix = parent.getFilesystemPrefix();
        this.completePath = parent.getCompletePath() + name + "/";
        this.sysCompletePath = getFilesystemPrefix() + completePath;
        this.parent = parent;
        this.name = name;

        if (!filenameRegex.matcher(name).matches()) {
            throw new InvalidParameterException("Folder name '" + completePath + "' contains illegal characters");
        }
        refresh();
    }

    public Folder(@NotNull String rootPath) {
        while (rootPath.endsWith("/")) {
            rootPath = rootPath.substring(0, rootPath.length() - 1);
        }
        sysPrefix = rootPath;
        this.completePath = "/";
        this.sysCompletePath = rootPath + completePath;
        this.name = "";
        this.parent = null;
        refresh();
    }

    public void refresh() {
        List<Folder> folders = new ArrayList<>();
        List<File> files = new ArrayList<>();
        try {
            Files.list(Paths.get(sysCompletePath)).forEach(path -> {
                java.io.File jfile = path.toFile();
                try {
                    if (jfile.isDirectory()) {
                        folders.add(new Folder(this, jfile.getName()));
                    } else if (jfile.isFile()) {
                        files.add(new File(this, jfile.getName()));
                    } else {
                        logger.warn("Path '" + path.toAbsolutePath() + "' is neither a folder, nor a file");
                    }
                } catch (InvalidParameterException | RuntimeIOException exception) {
                    logger.warn(exception.getMessage());
                }
            });
            this.folders = folders;
            this.files = files;
            refreshMeta();
        } catch (IOException exception) {
            logger.warn("Could not enumerate folder '" + sysCompletePath + "': " + exception.getClass().getName());
        }
    }

    public void refreshMeta() {
        lastModifiedTimeUnixEpoch = Math.max(folders.stream().mapToLong(Folder::getLastModifiedTimeUnixEpoch).max().orElse(0),
                files.stream().mapToLong(File::getLastModifiedTimeUnixEpoch).max().orElse(0));
        size = folders.stream().mapToLong(Folder::getSize).sum() + files.stream().mapToLong(File::getSize).sum();
    }

    public Folder getParent() {
        return parent;
    }

    public String getCompletePath() {
        return completePath;
    }

    public String getName() {
        return name;
    }

    public long getLastModifiedTimeUnixEpoch() {
        return lastModifiedTimeUnixEpoch;
    }

    public long getSize() {
        return size;
    }

    public String getFilesystemPrefix() {
        return sysPrefix;
    }

    public String getSysCompletePath() {
        return sysCompletePath;
    }

    public int countFiles() {
        return files.size();
    }

    public int countFolders() {
        return folders.size();
    }

    public Stream<File> fileStream() {
        return files.stream();
    }

    public Stream<Folder> folderStream() {
        return folders.stream();
    }

    public Stream<Folder> deepFolderStream() {
        return Stream.concat(Stream.of(this), folders.stream().flatMap(Folder::deepFolderStream));
    }

    public Stream<File> deepFileStream() {
        return Stream.concat(files.stream(), folders.stream().flatMap(Folder::deepFileStream));
    }

    File getFile(String path) {
        if (path.contains("/")) {
            int slashIndex = path.indexOf('/');
            String next = path.substring(0, slashIndex);
            return folders.stream().filter(folder -> folder.getName().equals(next)).findAny().map(folder -> folder.getFile(path.substring(slashIndex + 1))).orElse(null);
        } else {
            return files.stream().filter(file -> file.getName().equals(path)).findAny().orElse(null);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Folder[");
        sb.append("completePath='").append(completePath).append('\'');
        sb.append(", size=").append(size);
        sb.append(']');
        return sb.toString();
    }
}
