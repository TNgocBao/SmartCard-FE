package vn.kma.desktop.api;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

public final class IdpApi {
    public final ObjectMapper json=new ObjectMapper(JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(262144).build()).enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private record Session(String access,String refresh,long generation) {}
    private final AtomicReference<Session> session=new AtomicReference<>(new Session("","",0));
    private final Object refreshLock=new Object();
    private final String base;private final ApiTransport transport;
    public IdpApi(String base){this(base,new HttpApiTransport());}
    public IdpApi(String base,ApiTransport transport) {
        URI uri;try{uri=URI.create(base);}catch(RuntimeException invalid){throw new ClientConfigurationException("INVALID_API_BASE_URL");}
        if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && java.util.Set.of("127.0.0.1","localhost").contains(uri.getHost()))))throw new ClientConfigurationException("INVALID_API_BASE_URL");
        this.base=base.replaceAll("/$","");this.transport=transport;
    }
    public void session(JsonNode data){var pair=tokenPair(data);session.updateAndGet(old->new Session(pair.access(),pair.refresh(),old.generation()+1));}
    public void session(JsonNode data,long expectedVersion){
        var pair=tokenPair(data);session.updateAndGet(old->{if(old.generation()!=expectedVersion)throw new ClientStateException("SESSION_CHANGED");return new Session(pair.access(),pair.refresh(),old.generation()+1);});
    }
    public long sessionVersion(){return session.get().generation();}
    public String baseUrl(){return base;}
    public void requireVersion(long version){if(sessionVersion()!=version)throw new ClientStateException("SESSION_CHANGED");}
    public void clear(){session.updateAndGet(old->new Session("","",old.generation()+1));}
    public boolean hasSession(){return !session.get().access().isBlank();}
    public JsonNode call(String method,String path,Object payload) throws Exception {
        Session observed=session.get();var response=send(method,path,payload,observed.access());
        unchanged(observed);
        if(response.statusCode()==401 && !observed.refresh().isBlank() && !path.startsWith("/api/v1/auth/") && errorCode(response).equals("INVALID_OR_REVOKED_SESSION")) {
            Session renewed=refresh(observed);
            unchanged(observed);
            response=send(method,path,payload,renewed.access());
            unchanged(observed);
            if(response.statusCode()==401 && errorCode(response).equals("INVALID_OR_REVOKED_SESSION"))session.compareAndSet(renewed,new Session("","",renewed.generation()+1));
        }
        if(response.statusCode()>=300)throw failure(response);
        return parse(response.body());
    }
    private void unchanged(Session observed){if(session.get().generation()!=observed.generation())throw new ClientStateException("SESSION_CHANGED");}
    private Session tokenPair(JsonNode data){
        var access=data.path("accessToken");var refresh=data.path("refreshToken");
        if(!access.isTextual()||!refresh.isTextual()||!access.asText().matches("[A-Za-z0-9._~-]{1,8192}")||!refresh.asText().matches("[A-Za-z0-9_-]{1,512}"))throw new ClientConfigurationException("INVALID_AUTH_RESPONSE");
        return new Session(access.asText(),refresh.asText(),0);
    }
    private Session refresh(Session observed)throws Exception {
        synchronized(refreshLock){
            Session current=session.get();unchanged(observed);
            if(!current.access().equals(observed.access()))return current;
            try{
                var response=send("POST","/api/v1/auth/refresh",Map.of("refreshToken",current.refresh()),"");
                if(response.statusCode()!=200)throw failure(response);
                var pair=tokenPair(parse(response.body()));var renewed=new Session(pair.access(),pair.refresh(),current.generation());
                if(!session.compareAndSet(current,renewed))throw new ClientStateException("SESSION_CHANGED");
                return renewed;
            }catch(Exception failure){
                // Rotation may have committed even when its response was lost. Never reuse an uncertain token.
                session.compareAndSet(current,new Session("","",current.generation()+1));
                if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
                throw failure;
            }
        }
    }
    private JsonNode parse(String body){try{JsonNode result=body.isBlank()?json.createObjectNode():json.readTree(body);if(result==null||!result.isObject())throw new IllegalArgumentException();return result;}catch(Exception e){throw new ClientStateException("INVALID_SERVER_RESPONSE");}}
    private String errorCode(ApiResponse response){try{String code=parse(response.body()).path("code").asText();return code.matches("[A-Z0-9_]{1,80}")?code:"REQUEST_REJECTED";}catch(Exception e){return "REQUEST_REJECTED";}}
    private ApiClientException failure(ApiResponse response){
        String id="";var fields=new java.util.ArrayList<String>();
        try{var data=parse(response.body());id=data.path("requestId").asText();for(var field:data.path("fields"))if(field.isTextual())fields.add(field.asText());}catch(IllegalStateException invalid){/* raw body is never retained */}
        return new ApiClientException(response.statusCode(),errorCode(response),id,fields);
    }
    private ApiResponse send(String method,String path,Object payload,String access) throws Exception {
        if(path==null||!path.startsWith("/") || path.startsWith("//"))throw new ClientConfigurationException("INVALID_API_PATH");
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(20)).header("Accept","application/json");
        if(!access.isBlank()&&!path.startsWith("/api/v1/auth/"))request.header("Authorization","Bearer "+access);
        if(payload!=null)request.header("Content-Type","application/json");
        return transport.send(request.method(method,payload==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build());
    }
}
