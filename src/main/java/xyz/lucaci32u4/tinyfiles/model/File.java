package xyz.lucaci32u4.tinyfiles.model;

import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidParameterException;
import java.util.regex.Pattern;

public class File {
    public static final Pattern filenameRegex = Pattern.compile("^(\\.?[\\w\\- ]+)+$");

    private final Folder parent;
    private final String sysCompletePath;
    private final String completePath;
    private final String name;
    private final String extension;

    private long lastModifiedTimeUnixEpoch;
    private long size;

    public File(@NotNull Folder parent, @NotNull String name) {
        this.completePath = parent.getCompletePath() + name + "/";
        this.sysCompletePath = parent.getFilesystemPrefix() + completePath;
        this.parent = parent;

        if (!filenameRegex.matcher(name).matches()) {
            throw new InvalidParameterException("File name '" + completePath + "' contains illegal characters");
        }

        this.name = name;
        String[] partsFilename = name.split(Pattern.quote("."));
        extension = partsFilename.length > 1 ? partsFilename[partsFilename.length - 1] : "";

        if (!Files.isReadable(Paths.get(sysCompletePath))) {
            throw new InvalidParameterException("File '" + completePath + "' is not readable");
        }

        refreshMetadata();
    }

    public void refreshMetadata() {
        try {
            lastModifiedTimeUnixEpoch = Files.getLastModifiedTime(Paths.get(sysCompletePath)).toMillis();
        } catch (IOException e) {
            throw new RuntimeIOException("Could not get timestamp for file '" + completePath + "'");
        }
        try {
            size = Files.size(Paths.get(sysCompletePath));
        } catch (IOException e) {
            throw new RuntimeIOException("Could not get file size for file '" + completePath + "'");
        }
    }

    public Folder getParent() {
        return parent;
    }

    public String getCompletePath() {
        return completePath;
    }

    public String getSysCompletePath() {
        return sysCompletePath;
    }

    public String getName() {
        return name;
    }

    public String getExtension() {
        return extension;
    }

    public long getLastModifiedTimeUnixEpoch() {
        return lastModifiedTimeUnixEpoch;
    }

    public long getSize() {
        return size;
    }

    public FileInputStream openInputStream() {
        try {
            return new FileInputStream(completePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not open file '" + completePath + "'");
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("File[");
        sb.append("completePath='").append(completePath).append('\'');
        sb.append(", size=").append(size);
        sb.append(']');
        return sb.toString();
    }
}
