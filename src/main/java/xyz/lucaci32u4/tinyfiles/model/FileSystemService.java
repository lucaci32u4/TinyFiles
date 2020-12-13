package xyz.lucaci32u4.tinyfiles.model;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

public class FileSystemService {
    public static final Logger logger = LoggerFactory.getLogger(FileSystemService.class);

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, r -> new Thread(r, "ModelWorker"));

    private final String prefix;
    private final Folder root;

    private final int refreshIntervalSeconds;
    private final WatchService watchService;

    private final BiMap<Folder, WatchKey> watches = HashBiMap.create();
    private final ConcurrentLinkedDeque<Folder> queuedUpdates = new ConcurrentLinkedDeque<>();

    public FileSystemService(String prefix, int refreshIntervalSeconds) {
        this.refreshIntervalSeconds = refreshIntervalSeconds;
        this.prefix = prefix;
        root = new Folder(prefix);
        executor.execute(() -> root.deepFolderStream().forEach(this::registerListener));

        WatchService service = null;
        try {
            service = Paths.get(prefix).getFileSystem().newWatchService();
        } catch (IOException exception) {
            logger.error("Could not create a watcher service, files will not be updated", exception);
        }
        watchService = service;

        executor.scheduleWithFixedDelay(new UpdateConsumer(), refreshIntervalSeconds, refreshIntervalSeconds, TimeUnit.SECONDS);
        new FileWatcherThread().start();

    }

    private void registerListener(Folder folder) {
        if (watchService != null && !watches.containsKey(folder)) {
            try {
                watches.put(folder, Paths.get(folder.getSysCompletePath()).register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY));
            } catch (IOException exception) {
                logger.error("Could not register watcher for folder '" + folder.getSysCompletePath() + "'", exception);
            }
        }
    }

    public CompletableFuture<InputStream> getFileContentStream(String path) {
        return CompletableFuture.supplyAsync(() -> {
            File f = root.getFile(path.substring(1));
            return f != null ? f.openInputStream() : null;
        });
    }

    private class UpdateConsumer implements Runnable {
        public final Logger logger = LoggerFactory.getLogger(UpdateConsumer.class);

        private final ConcurrentLinkedDeque<Folder> queuedUpdates = FileSystemService.this.queuedUpdates;
        private final Folder root = FileSystemService.this.root;

        @Override
        public void run() {
            // transfer from queue to local storage
            List<Folder> folders = new ArrayList<>(queuedUpdates.size());
            while (!queuedUpdates.isEmpty()) {
                Folder f = queuedUpdates.remove();
                if (f != null) {
                    folders.add(f);
                }
            }

            if (folders.isEmpty()) {
                return;
            }

            // optimize redundant updates
            List<Folder> markRemove = new ArrayList<>();
            for (var parent : folders) {
                if (parent.countFolders() == 0) {
                    continue;
                }
                for (var leaf : folders) {
                    if (parent == leaf) {
                        continue;
                    }
                    var cursor =  leaf;
                    while (cursor != null) {
                        if (parent == cursor) {
                            markRemove.add(leaf);
                            break;
                        }
                        cursor = cursor.getParent();
                    }

                }
            }
            folders.removeAll(markRemove);

            // send updates
            folders.forEach(Folder::refresh);
            root.refreshMeta();

            // clean old watches and add new ones
            List<Folder> additions = new ArrayList<>();
            BiMap<Folder, WatchKey> watch = HashBiMap.create(watches);
            root.deepFolderStream().forEach(folder -> {
                if (watch.containsKey(folder)) {
                    // no change
                    watch.remove(folder);
                } else {
                    Optional<Folder> old = watch.keySet().stream().filter(f -> f.getCompletePath().equals(folder.getCompletePath())).findAny();
                    if (old.isPresent()) {
                        // same location, new object
                        WatchKey wk = watch.remove(old.get());
                        watches.forcePut(folder, wk);
                    } else {
                        // new location
                        additions.add(folder);
                    }
                }
            });
            watch.forEach((folder, watchKey) -> {
                watchKey.cancel();
                watches.remove(folder);
            });
            additions.forEach(FileSystemService.this::registerListener);
            logger.info("Found {} files in {} folders", root.deepFileStream().count(), root.deepFolderStream().count());
        }
    }

    private class FileWatcherThread extends Thread {
        public FileWatcherThread() {
            super("FileWatcherThread");
        }

        @Override
        public void run() {
            if (FileSystemService.this.watchService == null) {
                return;
            }
            while (true) {
                try {
                    WatchKey wk = FileSystemService.this.watchService.take();
                    if (wk == null) {
                        break;
                    }
                    Folder folder = FileSystemService.this.watches.inverse().get(wk);
                    if (folder != null) {
                        FileSystemService.this.queuedUpdates.offer(folder);
                    }
                    wk.pollEvents();
                    wk.reset();
                } catch (InterruptedException iex) {
                    break;
                } catch (ClosedWatchServiceException ignored) {
                    // nothing
                }
            }
        }
    }
}
