package xyz.lucaci32u4.tinyfiles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.lucaci32u4.tinyfiles.model.FileSystemService;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String [] args) {
        FileSystemService fss = new FileSystemService("/home/ux/test/folders", 2);
        WebServer webServer = new WebServer();
        webServer.createRoutes(fss);
        webServer.start("127.0.0.1", 3000);
    }

}
