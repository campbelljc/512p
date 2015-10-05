package middle;

import java.net.URL;
import java.net.MalformedURLException;

public class WSClient {

    ResourceManagerImplService service;
    
    public CustomerInfo proxy;
    
    public WSClient(String serviceName, String serviceHost, int servicePort) 
 /*   throws MalformedURLException */ {
    
		try {
	        URL wsdlLocation = new URL("http", serviceHost, servicePort, 
	                "/" + serviceName + "/service?wsdl");
			
			System.out.println(wsdlLocation.toString());
            
	        service = new ResourceManagerImplService(wsdlLocation);
    
	        proxy = service.getResourceManagerImplPort();
		} catch(Exception e) {
			System.out.println("middle.WSClient exception: " + e);
		}
    }

}
