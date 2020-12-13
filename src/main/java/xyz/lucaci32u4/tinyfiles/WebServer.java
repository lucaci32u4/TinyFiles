package xyz.lucaci32u4.tinyfiles;

import io.javalin.Javalin;
import xyz.lucaci32u4.tinyfiles.model.FileSystemService;
import xyz.lucaci32u4.tinyfiles.model.Folder;

public class WebServer {
    private final Javalin app;

    public WebServer() {
        app = Javalin.create(javalinConfig -> javalinConfig.showJavalinBanner = false);
    }

    public void createRoutes(FileSystemService fss) {
        app.get("/raw/*", ctx -> {
            String resource = ctx.req.getPathInfo().substring("/raw".length());

            // check data validity
            if (!Folder.pathnameRegex.matcher(resource).matches()) {
                ctx.status(400); // bad request
                return;
            }

            // get file stream
            var futureStream = fss.getFileContentStream(resource);
            var stream = futureStream.get();
            if (stream == null) {
                ctx.status(404);
                return;
            }

            // send file stream
            ctx.result(stream);
        });
    }

    public void start(String host, int port) {
        app.start(host, port);
    }
}
