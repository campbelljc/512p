package server;

import java.net.URL;
import java.net.MalformedURLException;

public class WSClient {

    ResourceManagerImplMWService service;
    
    public ResourceManager proxy;
    
    public WSClient(String serviceName, String serviceHost, int servicePort) 
 /*   throws MalformedURLException */ {
    
		try {
	        URL wsdlLocation = new URL("http", serviceHost, servicePort, 
	                "/" + serviceName + "/service?wsdl");
			
			System.out.println(wsdlLocation.toString());
            
	        service = new ResourceManagerImplMWService(wsdlLocation);
    
	        proxy = service.getResourceManagerImplPort();
		} catch(Exception e) {
			System.out.println("server.WSClient exception: " + e);
		}
    }

}
