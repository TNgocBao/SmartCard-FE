package vn.kma.desktop;

import com.sun.net.httpserver.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.Method;
import java.util.*;

/** Invokes the real HTTP handler with in-memory exchanges: no listening sockets or fake card logic. */
public final class BridgeCardChecks {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static Method handler;
    private static int checks;
    private static void check(String name,boolean condition){if(!condition)throw new AssertionError(name);checks++;}
    private static Exchange call(String path,String method,String payload)throws Exception {
        var x=new Exchange(path,method,payload);handler.invoke(null,x);return x;
    }
    private static JsonNode post(String path,Object payload,int status)throws Exception {
        var x=call(path,"POST",JSON.writeValueAsString(payload));check(path+" status "+status,x.code==status);return JSON.readTree(x.output.toByteArray());
    }
    public static void main(String[] args)throws Exception {
        handler=WebApplication.class.getDeclaredMethod("handle",HttpExchange.class);handler.setAccessible(true);
        if(!"true".equalsIgnoreCase(System.getenv("IDP_DEMO_CARD"))){
            post("/bridge/demo/create",Map.of(),403);
            post("/bridge/card/read",Map.of("transport","SOFTWARE"),403);
            System.out.println("BRIDGE DEMO DISABLED CHECKS PASSED: "+checks);return;
        }
        check("Unknown bridge path",call("/bridge/nope","GET","").code==404);
        check("Read method enforced",call("/bridge/card/read","GET","").code==405);
        for(String malformed:List.of("", "null","[]","{", "{} {}", "{\"transport\":\"SOFTWARE\",\"transport\":\"PCSC\"}"))check("Malformed JSON is 400",call("/bridge/card/read","POST",malformed).code==400);
        post("/bridge/card/read",Map.of(),400);
        post("/bridge/card/read",Map.of("transport","REMOTE"),400);
        post("/bridge/card/read",Map.of("transport","SOFTWARE","url","http://example.test"),400);
        post("/bridge/card/read",Map.of("transport",12),400);
        post("/bridge/card/read",Map.of("transport","SOFTWARE"),400); // No inserted card.
        var created=post("/bridge/demo/create",Map.of(),200);String id=created.path("inserted").asText();
        check("White card reports missing applet",post("/bridge/card/read",Map.of("transport","SOFTWARE"),400).path("code").asText().equals("APPLET_NOT_FOUND"));
        post("/bridge/demo/install",Map.of(),200);
        var identity=post("/bridge/card/read",Map.of("transport","SOFTWARE"),200);
        check("Read matches inserted identity",identity.path("identifier").asText().equals(id));
        for(String invalid:List.of("12345","1234567890123","abcdef","123 56"))check("PIN format stable code",post("/bridge/card/verify-pin",Map.of("transport","SOFTWARE","cardIdentifier",id,"pin",invalid),400).path("code").asText().equals("PIN_FORMAT_INVALID"));
        for(String payload:List.of("???",Base64.getEncoder().encodeToString(new byte[31]),Base64.getEncoder().encodeToString(new byte[101])))check("Invalid challenge rejected",post("/bridge/card/sign",Map.of("transport","SOFTWARE","cardIdentifier",id,"pin","123456","payload",payload),400).path("code").asText().equals("CARD_CHALLENGE_INVALID"));
        var wrong=post("/bridge/card/verify-pin",Map.of("transport","SOFTWARE","cardIdentifier",id,"pin","000000"),400);
        check("Wrong PIN remaining tries",wrong.path("remainingTries").asInt()==4&&wrong.path("code").asText().equals("PIN_INVALID"));
        post("/bridge/card/verify-pin",Map.of("transport","SOFTWARE","cardIdentifier",id,"pin","123456"),200);
        check("Identity swap detected",post("/bridge/card/verify-pin",Map.of("transport","SOFTWARE","cardIdentifier","A".repeat(32),"pin","123456"),400).path("code").asText().equals("CARD_CHANGED"));
        post("/bridge/card/pin",Map.of("transport","SOFTWARE","cardIdentifier",id,"pin","123456","newPin","654321"),200);
        var signed=post("/bridge/card/sign",Map.of("transport","SOFTWARE","cardIdentifier",id,"pin","654321","payload",Base64.getEncoder().encodeToString(new byte[57])),200);
        check("Signature length",Base64.getDecoder().decode(signed.path("signature").asText()).length==256);
        post("/bridge/demo/remove",Map.of(),200);
        check("Removed card error",post("/bridge/card/read",Map.of("transport","SOFTWARE"),400).path("code").asText().equals("CARD_NOT_AVAILABLE"));
        post("/bridge/demo/insert",Map.of("cardIdentifier",id),200);
        var status=call("/bridge/demo/status","GET","");
        check("Trace redacts secrets",!status.output.toString(java.nio.charset.StandardCharsets.UTF_8).contains("654321"));
        for(String badOrigin:List.of("https://untrusted.test","null")){
            var x=new Exchange("/bridge/demo/create","POST","{}");x.headers.set("Origin",badOrigin);handler.invoke(null,x);check("Origin denied",x.code==403);
        }
        var x=new Exchange("/bridge/demo/create","POST","{}");x.headers.remove("Origin");handler.invoke(null,x);check("Missing origin denied",x.code==403);
        x=new Exchange("/bridge/demo/create","POST","{}");x.headers.set("Host","attacker.test");handler.invoke(null,x);check("Host denied",x.code==403);
        x=new Exchange("/bridge/demo/create","POST","{}");x.headers.set("Content-Type","text/plain");handler.invoke(null,x);check("Content type denied",x.code==403);
        check("Body limit enforced",call("/bridge/card/read","POST"," ".repeat(262145)).code==400);
        System.out.println("BRIDGE CARD HANDLER CHECKS PASSED: "+checks);
    }
    private static final class Exchange extends HttpExchange {
        final Headers headers=new Headers(),response=new Headers();final URI uri;final String method;final InputStream input;final ByteArrayOutputStream output=new ByteArrayOutputStream();int code;
        Exchange(String path,String method,String payload){this.uri=URI.create(path);this.method=method;this.input=new ByteArrayInputStream(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));headers.set("Host","127.0.0.1:8787");headers.set("Origin","http://127.0.0.1:8787");headers.set("Content-Type","application/json");}
        public Headers getRequestHeaders(){return headers;}public Headers getResponseHeaders(){return response;}public URI getRequestURI(){return uri;}public String getRequestMethod(){return method;}public HttpContext getHttpContext(){return null;}public void close(){}public InputStream getRequestBody(){return input;}public OutputStream getResponseBody(){return output;}public void sendResponseHeaders(int status,long length){code=status;}public InetSocketAddress getRemoteAddress(){return new InetSocketAddress("127.0.0.1",12345);}public int getResponseCode(){return code;}public InetSocketAddress getLocalAddress(){return new InetSocketAddress("127.0.0.1",8787);}public String getProtocol(){return "HTTP/1.1";}public Object getAttribute(String name){return null;}public void setAttribute(String name,Object value){}public void setStreams(InputStream in,OutputStream out){}public HttpPrincipal getPrincipal(){return null;}
    }
}
