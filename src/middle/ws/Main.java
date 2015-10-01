package middle.ws;

import java.io.File;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import middle.*;

public class Main {

    public static void main(String[] args) 
    throws Exception {
    
        if (args.length != 3) {
            System.out.println(
                "Usage: java Main <service-name> <service-port> <deploy-dir>");
            System.exit(-1);
        }
    
        String serviceName = args[0];
        int port = Integer.parseInt(args[1]);
        String deployDir = args[2];
    
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setBaseDir(deployDir);

        tomcat.getHost().setAppBase(deployDir);
        tomcat.getHost().setDeployOnStartup(true);
        tomcat.getHost().setAutoDeploy(true);

        //tomcat.addWebapp("", new File(deployDir).getAbsolutePath());

        tomcat.addWebapp("/" + serviceName, 
                new File(deployDir + "/" + serviceName).getAbsolutePath());

		tomcat.enableNaming();
	
        tomcat.start();

        tomcat.getServer().await();
    }
    
}
