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
    
        if (args.length != 6) {
            System.out.println(
                "Usage: java Main <service-name> <service-port> <deploy-dir>");
            System.exit(-1);
        }
    
        String serviceName = args[0];
        int port = Integer.parseInt(args[1]);
        String deployDir = args[2];
	String rm1 = args[3]; // test
	String rm2 = args[4];
	String rm3 = args[5];
    
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setBaseDir(deployDir);

        tomcat.getHost().setAppBase(deployDir);
        tomcat.getHost().setDeployOnStartup(true);
        tomcat.getHost().setAutoDeploy(true);

        //tomcat.addWebapp("", new File(deployDir).getAbsolutePath());

        tomcat.addWebapp("/" + serviceName, 
                new File(deployDir + "/" + serviceName).getAbsolutePath());

        tomcat.start();

	Server middlewareServer = tomcat.getServer();
	Service rmImplMW = middlewareServer.findServices()[0];
	System.out.println("***********Service: " + rmImplMW.getName());
	
	System.out.println(tomcat.getServletContext().getInitParameter("rm1"));
((ResourceManagerImplMW)rmImplMW).setRMs(rm1, rm2, rm3);

        tomcat.getServer().await();
    }
    
}
