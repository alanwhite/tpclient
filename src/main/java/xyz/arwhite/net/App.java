package xyz.arwhite.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws IOException, InterruptedException
    {
        System.out.println( "Hello World!" );
        
        // first make http proxy call to tinyproxy listening on 8888
        
        System.out.println("Java 11 http client direct to tinyproxy");
        
        var plainClient = HttpClient.newBuilder()
        		.proxy(ProxySelector.of(new InetSocketAddress("localhost",8888)))
        		.build();
        
        HttpRequest tinyGet = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("https://httpbin.org/ip")) 
                .build();
        
        HttpResponse<String> tinyResp = plainClient.send(tinyGet, BodyHandlers.ofString());
        
        System.out.println(tinyResp.statusCode());
        System.out.println(tinyResp.body());
       
        //-------------------
        
        System.out.println("pre-Java 11 http client direct to tinyproxy");
        
        URL weburl = new URL("https://httpbin.org/ip");
        Proxy tinyProxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 8888));
        HttpURLConnection connection = (HttpURLConnection) weburl.openConnection(tinyProxy);
       
        connection.setRequestMethod("GET");
		int responseCode = connection.getResponseCode();
		System.out.println(responseCode);
		
		BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		String inputLine;

		while ((inputLine = in.readLine()) != null) {
			System.out.println(inputLine);
		}
		in.close();

        //-------------------
		
        System.out.println("Java 11 http client TLS via stunnel to tinyproxy");
        
        System.out.println("NOT IMPLEMENTED YET");
        
        System.out.println("pre-Java 11 http client TLS via stunnel to tinyproxy");
        
        System.out.println("NOT IMPLEMENTED YET");

    }
}
