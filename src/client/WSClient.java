package client;

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
                
		        service = new ResourceManagerImplMWService(wsdlLocation);
        
		        proxy = service.getResourceManagerImplMWPort();
		} catch(Exception e) {}
    }

}
