package vn.kma.desktop;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import vn.kma.desktop.api.*;

/** Deterministic tests of the production API client with injected transport, no sockets or backend sources. */
public final class FrontendApiChecks {
    private final List<Map<String,Object>> cases=new ArrayList<>();
    public static void main(String[] args) throws Exception {
        var checks=new FrontendApiChecks();
        try{checks.run();}finally{checks.report();}
        System.out.println("FRONTEND API CHECKS PASSED: "+checks.cases.size());
    }
    private void check(String name,boolean passed){cases.add(Map.of("case",name,"passed",passed));if(!passed)throw new AssertionError(name);}
    private com.fasterxml.jackson.databind.JsonNode tokens(IdpApi api,String access,String refresh){return api.json.valueToTree(Map.of("accessToken",access,"refreshToken",refresh));}
    private void run() throws Exception {
        AtomicInteger refreshes=new AtomicInteger(),calls=new AtomicInteger();
        IdpApi api=new IdpApi("http://127.0.0.1:8080",request->{
            calls.incrementAndGet();
            if(request.uri().getPath().equals("/api/v1/auth/refresh")){
                check("Refresh request omits expired Authorization header",request.headers().firstValue("Authorization").isEmpty());refreshes.incrementAndGet();return new ApiResponse(200,"{\"accessToken\":\"fresh\",\"refreshToken\":\"rotated\"}");
            }
            return request.headers().firstValue("Authorization").orElse("").equals("Bearer fresh")?new ApiResponse(200,"{\"ok\":true}"):new ApiResponse(401,"{\"code\":\"INVALID_OR_REVOKED_SESSION\"}");
        });
        api.session(tokens(api,"expired","original"));
        check("Expired access refreshes and retries successfully",api.call("GET","/api/v1/me",null).path("ok").asBoolean());
        check("Exactly one refresh and one retry",refreshes.get()==1&&calls.get()==3);
        AtomicInteger domainRefreshes=new AtomicInteger();
        var domain=new IdpApi("http://localhost:8080",request->{if(request.uri().getPath().contains("refresh"))domainRefreshes.incrementAndGet();return new ApiResponse(401,"{\"code\":\"AUTHENTICATION_FAILED\"}");});
        domain.session(tokens(domain,"access","refresh"));
        boolean domainFailure=false;try{domain.call("POST","/api/v1/me/totp/verify",Map.of("code","000000"));}catch(IllegalStateException e){domainFailure=e.getMessage().contains("AUTHENTICATION_FAILED");}
        check("Business 401 is not replayed or refreshed",domainFailure&&domainRefreshes.get()==0);
        AtomicInteger timeoutCalls=new AtomicInteger();
        var timeout=new IdpApi("https://idp.example.test",request->{timeoutCalls.incrementAndGet();throw new java.net.http.HttpTimeoutException("timeout");});
        try{timeout.call("POST","/api/v1/accounts",Map.of("username","new-user"));}catch(java.net.http.HttpTimeoutException expected){}
        check("Timed-out mutation is never automatically resubmitted",timeoutCalls.get()==1);
        var malformed=new IdpApi("http://localhost:8080",request->new ApiResponse(500,"private-data-must-not-be-logged"));
        String message="";try{malformed.call("GET","/api/v1/me",null);}catch(IllegalStateException e){message=e.getMessage();}
        check("Malformed server errors reveal no response body",!message.contains("private-data")&&message.contains("HTTP 500"));
        boolean insecure=false;try{new IdpApi("http://public.example.test");}catch(IllegalArgumentException expected){insecure=true;}check("Non-local HTTP is rejected",insecure);
        boolean credentials=false;try{new IdpApi("https://user:password@idp.example.test");}catch(IllegalArgumentException expected){credentials=true;}check("Credentials embedded in base URL are rejected",credentials);
        boolean unsafePath=false;try{api.call("GET","//attacker.example.test",null);}catch(IllegalArgumentException expected){unsafePath=true;}check("Authority-changing paths are rejected",unsafePath);
        concurrentRefresh();sessionSwitch();bodyLimits();refreshFailureCases();
    }
    private void concurrentRefresh() throws Exception {
        var barrier=new CyclicBarrier(2);var count=new AtomicInteger();
        var api=new IdpApi("http://localhost:8080",request->{
            if(request.uri().getPath().equals("/api/v1/auth/refresh")){count.incrementAndGet();return new ApiResponse(200,"{\"accessToken\":\"new\",\"refreshToken\":\"new-refresh\"}");}
            if(request.headers().firstValue("Authorization").orElse("").equals("Bearer old")){barrier.await(5,TimeUnit.SECONDS);return new ApiResponse(401,"{\"code\":\"INVALID_OR_REVOKED_SESSION\"}");}
            return new ApiResponse(200,"{\"ok\":true}");
        });
        api.session(tokens(api,"old","old-refresh"));
        try(var executor=Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->api.call("GET","/api/v1/me",null));var second=executor.submit(()->api.call("GET","/api/v1/me/sessions",null));
            check("Concurrent expired requests both recover",first.get(10,TimeUnit.SECONDS).path("ok").asBoolean()&&second.get(10,TimeUnit.SECONDS).path("ok").asBoolean());
        }
        check("Concurrent requests rotate refresh only once",count.get()==1);
    }
    private void sessionSwitch() throws Exception {
        var holder=new AtomicReference<IdpApi>();var requests=new AtomicInteger();
        var api=new IdpApi("http://localhost:8080",request->{requests.incrementAndGet();if(request.uri().getPath().equals("/api/v1/auth/refresh")){holder.get().clear();return new ApiResponse(200,"{\"accessToken\":\"stale\",\"refreshToken\":\"stale-refresh\"}");}return new ApiResponse(401,"{\"code\":\"INVALID_OR_REVOKED_SESSION\"}");});
        holder.set(api);api.session(tokens(api,"expired","refresh"));boolean rejected=false;
        try{api.call("POST","/api/v1/accounts",Map.of("username","user"));}catch(IllegalStateException e){rejected=e.getMessage().equals("SESSION_CHANGED");}
        check("Sign-out during refresh cannot restore the old session",rejected&&!api.hasSession());
        check("Session switch prevents mutation retry",requests.get()==2);
    }
    private void bodyLimits() throws Exception {
        var handler=new BoundedJsonBodyHandler(8);var subscriber=handler.subscriber();var cancelled=new AtomicBoolean();
        subscriber.onSubscribe(new Flow.Subscription(){public void request(long n){}public void cancel(){cancelled.set(true);}});
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[9])));boolean rejected=false;
        try{subscriber.getBody().toCompletableFuture().join();}catch(CompletionException expected){rejected=true;}
        check("Oversized response is cancelled while receiving",rejected&&cancelled.get());
        var small=new BoundedJsonBodyHandler(100).subscriber();small.onSubscribe(new Flow.Subscription(){public void request(long n){}public void cancel(){}});
        byte[] unicode="Nguyễn".getBytes(StandardCharsets.UTF_8);small.onNext(List.of(ByteBuffer.wrap(Arrays.copyOfRange(unicode,0,3))));small.onNext(List.of(ByteBuffer.wrap(Arrays.copyOfRange(unicode,3,unicode.length))));small.onComplete();
        check("UTF-8 response survives split byte buffers",small.getBody().toCompletableFuture().get().equals("Nguyễn"));
    }
    private void refreshFailureCases()throws Exception {
        String[] badBodies={"not-json-private-token","{\"accessToken\":\"fresh\"}","{\"accessToken\":\"a\",\"accessToken\":\"b\",\"refreshToken\":\"r\"}","{\"accessToken\":\"a\",\"refreshToken\":\"r\"} {}"};
        String[] labels={"malformed JSON","missing token pair","duplicate JSON field","trailing JSON value"};
        for(int i=0;i<badBodies.length;i++){
            String bad=badBodies[i];var calls=new AtomicInteger();
            var api=new IdpApi("http://localhost:8080",request->{calls.incrementAndGet();return request.uri().getPath().contains("refresh")?new ApiResponse(200,bad):new ApiResponse(401,"{\"code\":\"INVALID_OR_REVOKED_SESSION\"}");});
            api.session(tokens(api,"old","old-refresh"));boolean rejected=false;
            try{api.call("POST","/api/v1/accounts",Map.of("username","test"));}catch(IllegalStateException|IllegalArgumentException expected){rejected=true;}
            check("Refresh with "+labels[i]+" clears uncertain session without replay",rejected&&!api.hasSession()&&calls.get()==2);
        }
        var timeouts=new AtomicInteger();
        var timeout=new IdpApi("http://localhost:8080",request->{timeouts.incrementAndGet();if(request.uri().getPath().contains("refresh"))throw new java.net.http.HttpTimeoutException("request timed out");return new ApiResponse(401,"{\"code\":\"INVALID_OR_REVOKED_SESSION\"}");});
        timeout.session(tokens(timeout,"old","old-refresh"));try{timeout.call("POST","/api/v1/accounts",Map.of());}catch(java.net.http.HttpTimeoutException expected){}
        check("Lost refresh response clears tokens and never replays mutation",!timeout.hasSession()&&timeouts.get()==2);
        var retried=new AtomicInteger();
        var rejected=new IdpApi("http://localhost:8080",request->{retried.incrementAndGet();return request.uri().getPath().contains("refresh")?new ApiResponse(200,"{\"accessToken\":\"fresh\",\"refreshToken\":\"fresh-refresh\"}"):new ApiResponse(401,"{\"code\":\"INVALID_OR_REVOKED_SESSION\"}");});
        rejected.session(tokens(rejected,"old","old-refresh"));try{rejected.call("GET","/api/v1/me",null);}catch(IllegalStateException expected){}
        check("Bearer rejection after refresh clears session with one bounded retry",!rejected.hasSession()&&retried.get()==3);
        var reference=new AtomicReference<IdpApi>();
        var switched=new IdpApi("http://localhost:8080",request->{if(request.uri().getPath().contains("refresh")){var api=reference.get();api.session(tokens(api,"another-user","another-refresh"));return new ApiResponse(401,"{\"code\":\"INVALID_REFRESH_TOKEN\"}");}return new ApiResponse(401,"{\"code\":\"INVALID_OR_REVOKED_SESSION\"}");});
        reference.set(switched);switched.session(tokens(switched,"old","old-refresh"));try{switched.call("GET","/api/v1/me",null);}catch(IllegalStateException expected){}
        check("Failure from an old refresh cannot clear a newer user's session",switched.hasSession());
        var lateReference=new AtomicReference<IdpApi>();
        var late=new IdpApi("http://localhost:8080",request->{lateReference.get().clear();return new ApiResponse(200,"{\"username\":\"old-user\"}");});
        lateReference.set(late);late.session(tokens(late,"old","old-refresh"));boolean dropped=false;
        try{late.call("GET","/api/v1/me",null);}catch(IllegalStateException expected){dropped=expected.getMessage().equals("SESSION_CHANGED");}
        check("Successful response arriving after logout is discarded",dropped&&!late.hasSession());
        var failedCount=new AtomicInteger();var unavailable=new IdpApi("http://localhost:8080",request->{failedCount.incrementAndGet();return new ApiResponse(503,"{\"code\":\"UNAVAILABLE\"}");});
        unavailable.session(tokens(unavailable,"active","refresh"));try{unavailable.call("POST","/api/v1/accounts",Map.of());}catch(IllegalStateException expected){}
        check("Server 503 never replays a mutation or clears valid tokens",failedCount.get()==1&&unavailable.hasSession());
        var publicApi=new IdpApi("http://localhost:8080",request->new ApiResponse(200,request.headers().firstValue("Authorization").isEmpty()?"{\"noBearer\":true}":"{}"));
        publicApi.session(tokens(publicApi,"active","refresh"));check("Public authentication requests omit bearer from an existing session",publicApi.call("POST","/api/v1/auth/password",Map.of()).path("noBearer").asBoolean());
    }
    private void report() throws Exception {
        var path=java.nio.file.Path.of("target/api-test-results.json");java.nio.file.Files.createDirectories(path.getParent());
        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(),Map.of("recordedAt",java.time.Instant.now().toString(),"transport","injected deterministic transport; no network","cases",cases));
    }
}
