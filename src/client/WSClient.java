package client;

import java.net.URL;
import java.net.MalformedURLException;


public class WSClient {

    ResourceManagerImplMWService service;
    
    ResourceManager proxy;
    
    public WSClient(String serviceName, String serviceHost, int servicePort) 
    throws MalformedURLException {
    
        URL wsdlLocation = new URL("http", serviceHost, servicePort, 
                "/" + serviceName + "/service?wsdl");
                
        service = new ResourceManagerImplMWService(wsdlLocation);
        
        proxy = service.getResourceManagerImplMWPort();
    }

}
