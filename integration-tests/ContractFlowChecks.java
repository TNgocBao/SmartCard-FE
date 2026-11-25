package vn.kma.desktop.integration;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import javax.smartcardio.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.boot.*;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.http.HttpMethod;
import vn.kma.idp.IdpApplication;
import vn.kma.desktop.api.*;
import vn.kma.desktop.card.*;
import vn.kma.desktop.service.SmartCardAuthenticationService;

/** Optional cross-repository contract harness. Uses real FE/BE business code; only I/O boundaries are simulated. */
public final class ContractFlowChecks {
    private final List<Map<String,Object>> cases=new ArrayList<>();
    private final ObjectMapper json=new ObjectMapper();
    public static void main(String[] args) throws Exception {
        vn.kma.idp.NetworkIsolation.install();
        var checks=new ContractFlowChecks();
        try(var context=new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());context.getEnvironment().setActiveProfiles("test");
            new ConfigDataApplicationContextInitializer().initialize(context);context.register(IdpApplication.class);context.refresh();
            context.getBean(ApplicationRunner.class).run(new DefaultApplicationArguments(new String[0]));
            MockMvc mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
            try {checks.run(mvc,context.getBean(vn.kma.idp.shared.Database.class));} finally {checks.report();}
        }
        System.out.println("FE-BE CONTRACT CHECKS PASSED: "+checks.cases.size());
    }
    private void check(String label,boolean passed){cases.add(Map.of("case",label,"passed",passed));if(!passed)throw new AssertionError(label);}
    private void run(MockMvc mvc,vn.kma.idp.shared.Database db) throws Exception {
        var adminTransport=new MvcTransport(mvc);var admin=new IdpApi("http://127.0.0.1:8080",adminTransport);
        admin.session(admin.call("POST","/api/v1/auth/password",Map.of("username","admin","password","Test-only-password-2026")));
        String adminId=admin.call("GET","/api/v1/me",null).path("data").path("id").asText();
        check("FE password login reaches real BE and creates admin session",!adminId.isBlank());
        var invite=admin.call("POST","/api/v1/accounts/"+adminId+"/activation-invite",Map.of());
        var activated=admin.call("POST","/api/v1/auth/activate",Map.of("token",invite.path("oneTimeActivationToken").asText()));
        String enrollToken=activated.path("enrollmentToken").asText();
        var setup=admin.call("POST","/api/v1/auth/enrollment/totp/setup",Map.of("token",enrollToken));
        String code=totp(setup.path("secret").asText(),0);
        var adminMfa=admin.call("POST","/api/v1/auth/enrollment/totp/verify",Map.of("enrollmentToken",enrollToken,"code",code));
        admin.session(adminMfa);check("FE enrollment obtains real AAL2 admin token",admin.hasSession());
        String account=admin.call("POST","/api/v1/accounts",Map.of("username","contract01","displayName","Contract User","email","contract@example.test")).path("accountId").asText();
        check("FE account creation executes BE transaction",!account.isBlank());
        var card=new DemoCardReader();card.create();card.install();var transport=new MvcTransport(mvc);var user=new IdpApi("http://127.0.0.1:8080",transport);
        var cardService=new SmartCardAuthenticationService(()->user,simulator->card.open());
        var identity=cardService.read(true);
        check("Production CardClient reads simulated APDU public key and identity",identity.identifier().length()==32&&!identity.publicKeySpki().isBlank());
        admin.call("POST","/api/v1/accounts/"+account+"/smart-cards",Map.of("cardIdentifier",identity.identifier(),"publicKeySpki",identity.publicKeySpki(),"transport","SOFTWARE"));
        // Validation and transaction rollback matrix, via the same security/controller stack.
        for(Object body:List.of(Map.of(),Map.of("username"," ","cardIdentifier",identity.identifier()),Map.of("username","contract01","cardIdentifier","bad"),Map.of("username","x".repeat(129),"cardIdentifier",identity.identifier()),Map.of("username","contract01","cardIdentifier",identity.identifier(),"pin","123456")))expect(mvc,"POST","/api/v1/auth/smart-card/challenge",body,null,400);
        for(Object body:List.of(Map.of(),Map.of("challengeId","bad","signature","abc"),Map.of("challengeId",UUID.randomUUID().toString(),"signature"," "),Map.of("challengeId",UUID.randomUUID().toString(),"signature","x".repeat(1025))))expect(mvc,"POST","/api/v1/auth/smart-card/verify",body,null,400);
        expect(mvc,"POST","/api/v1/auth/smart-card/verify",Map.of("challengeId",UUID.randomUUID().toString(),"signature","YWJj"),null,401);
        expect(mvc,"GET","/api/v1/me/smart-cards",null,null,401);
        expect(mvc,"GET","/api/v1/me/smart-cards?page=-1",null,adminTransport.lastTokens.path("accessToken").asText(),400);
        for(Object body:List.of(Map.of("username","ab","displayName","Test"),Map.of("username","bad user","displayName","Test"),Map.of("username","validuser","displayName"," "),Map.of("username","validuser","displayName","Test","email","invalid")))expect(mvc,"POST","/api/v1/accounts",body,adminTransport.lastTokens.path("accessToken").asText(),400);
        for(Object body:List.of(Map.of("cardIdentifier",identity.identifier(),"publicKeySpki","bad-key","transport","SOFTWARE"),Map.of("cardIdentifier",identity.identifier(),"publicKeySpki",identity.publicKeySpki(),"transport","REMOTE"),Map.of("cardIdentifier","bad","publicKeySpki",identity.publicKeySpki(),"transport","SOFTWARE")))expect(mvc,"POST","/api/v1/accounts/"+account+"/smart-cards",body,adminTransport.lastTokens.path("accessToken").asText(),400);
        long before=db.jdbc.queryForObject("SELECT COUNT(*) FROM credential WHERE account_id=?",Long.class,account);
        expect(mvc,"POST","/api/v1/accounts/"+account+"/smart-cards",Map.of("cardIdentifier",identity.identifier(),"publicKeySpki",identity.publicKeySpki(),"transport","SOFTWARE"),adminTransport.lastTokens.path("accessToken").asText(),409);
        check("Duplicate registration rolls back without orphan credentials",db.jdbc.queryForObject("SELECT COUNT(*) FROM credential WHERE account_id=?",Long.class,account)==before);
        char[] wrong="000000".toCharArray();boolean badPin=false;try{cardService.authenticate(true,"contract01",wrong);}catch(IllegalStateException expected){badPin=expected.getMessage().contains("PIN");}
        check("Wrong card PIN prevents FE from receiving a token",badPin&&!user.hasSession());
        check("PIN char array is cleared after a failed card attempt",Arrays.equals(wrong,new char[wrong.length]));
        char[] pin="123456".toCharArray();var profile=cardService.authenticate(true,"contract01",pin);
        check("FE APDU signature is verified by real BE",profile.path("data").path("id").asText().equals(account));

        JsonNode originalTokens=transport.lastTokens;
        var savedCards=user.call("GET","/api/v1/me/smart-cards",null).path("data");
        check("Card registration persisted in database",savedCards.size()==1&&savedCards.get(0).path("card_identifier").asText().equals(identity.identifier()));
        check("Software card transport is correctly persisted",savedCards.get(0).path("transport").asText().equals("SOFTWARE"));
        check("Card list excludes public key payload",!savedCards.toString().contains("public_key_spki"));
        check("Card proof success recorded in database",user.call("GET","/api/v1/me/smart-cards/history",null).path("data").toString().contains("CARD_PROOF"));
        boolean crossAccount=false;try{user.call("GET","/api/v1/me/smart-cards?accountId="+adminId,null);}catch(IllegalStateException expected){crossAccount=expected.getMessage().contains("403");}
        check("User cannot read another account card inventory",crossAccount);
        var proofChallenge=user.call("POST","/api/v1/auth/smart-card/challenge",Map.of("username","contract01","cardIdentifier",identity.identifier()));
        byte[] proofSignature;try(var t=card.open()){proofSignature=new CardClient(t).sign(Base64.getDecoder().decode(proofChallenge.path("payload").asText()),"123456".toCharArray());}
        var validProof=Map.of("challengeId",proofChallenge.path("challengeId").asText(),"signature",Base64.getEncoder().encodeToString(proofSignature));
        proofSignature[0]^=1;
        boolean tampered=false;try{user.call("POST","/api/v1/auth/smart-card/verify",Map.of("challengeId",proofChallenge.path("challengeId").asText(),"signature",Base64.getEncoder().encodeToString(proofSignature)));}catch(IllegalStateException expected){tampered=expected.getMessage().contains("401");}
        check("Modified signature rejected by real backend",tampered);
        var proofTokens=user.call("POST","/api/v1/auth/smart-card/verify",validProof);
        // Close the additional test session without replacing the user's current one.
        var proofSession=new IdpApi("http://127.0.0.1:8080",new MvcTransport(mvc));proofSession.session(proofTokens);proofSession.call("POST","/api/v1/logout",Map.of());
        boolean replay=false;try{user.call("POST","/api/v1/auth/smart-card/verify",validProof);}catch(IllegalStateException expected){replay=expected.getMessage().contains("410");}
        check("Replayed proof rejected",replay);
        check("Failed proof recorded despite transaction rollback",user.call("GET","/api/v1/me/smart-cards/history",null).path("data").toString().contains("FAILURE"));

        var expiredChallenge=user.call("POST","/api/v1/auth/smart-card/challenge",Map.of("username","contract01","cardIdentifier",identity.identifier()));
        byte[] expiredSignature;try(var t=card.open()){expiredSignature=new CardClient(t).sign(Base64.getDecoder().decode(expiredChallenge.path("payload").asText()),"123456".toCharArray());}
        db.jdbc.update("UPDATE smart_card_challenge SET expires_at=? WHERE id=?",vn.kma.idp.shared.Time.format(java.time.Instant.now().minusSeconds(1)),expiredChallenge.path("challengeId").asText());
        boolean expired=false;try{user.call("POST","/api/v1/auth/smart-card/verify",Map.of("challengeId",expiredChallenge.path("challengeId").asText(),"signature",Base64.getEncoder().encodeToString(expiredSignature)));}catch(IllegalStateException expected){expired=expected.getMessage().contains("410");}
        check("Expired signed challenge rejected",expired);

        var raceChallenge=user.call("POST","/api/v1/auth/smart-card/challenge",Map.of("username","contract01","cardIdentifier",identity.identifier()));
        byte[] raceSignature;try(var t=card.open()){raceSignature=new CardClient(t).sign(Base64.getDecoder().decode(raceChallenge.path("payload").asText()),"123456".toCharArray());}
        String raceBody=json.writeValueAsString(Map.of("challengeId",raceChallenge.path("challengeId").asText(),"signature",Base64.getEncoder().encodeToString(raceSignature)));
        try(var executor=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);
            Callable<org.springframework.mock.web.MockHttpServletResponse> task=()->{start.await();return mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/smart-card/verify").contentType("application/json").content(raceBody)).andReturn().getResponse();};
            var first=executor.submit(task);var second=executor.submit(task);start.countDown();
            var responses=List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
            check("Concurrent proofs create exactly one accepted result",responses.stream().filter(r->r.getStatus()==200).count()==1&&responses.stream().filter(r->r.getStatus()==410).count()==1);
            for(var response:responses)if(response.getStatus()==200){var session=new IdpApi("http://127.0.0.1:8080",new MvcTransport(mvc));session.session(json.readTree(response.getContentAsString()));session.call("POST","/api/v1/logout",Map.of());}
        }



        check("PIN char array is cleared after successful authentication",Arrays.equals(pin,new char[pin.length]));
        check("PIN field never travels across the HTTP API",!transport.sawPinField);
        user.session(json.valueToTree(Map.of("accessToken","expired-token","refreshToken",originalTokens.path("refreshToken").asText())));
        check("Real FE refresh recovers from BE bearer rejection",user.call("GET","/api/v1/me",null).path("data").path("id").asText().equals(account));
        check("Refresh request carried no expired bearer header",transport.refreshWithoutBearer);
        boolean forbidden=false;try{user.call("GET","/api/v1/management/overview",null);}catch(IllegalStateException e){forbidden=e.getMessage().contains("403");}
        check("Real BE rejects management access from member FE",forbidden);
        var userInvite=admin.call("POST","/api/v1/accounts/"+account+"/activation-invite",Map.of());
        var userActivation=user.call("POST","/api/v1/auth/activate",Map.of("token",userInvite.path("oneTimeActivationToken").asText()));
        String userEnroll=userActivation.path("enrollmentToken").asText();
        var userSetup=user.call("POST","/api/v1/auth/enrollment/totp/setup",Map.of("token",userEnroll));
        user.call("POST","/api/v1/auth/enrollment/totp/verify",Map.of("enrollmentToken",userEnroll,"code",totp(userSetup.path("secret").asText(),0)));
        user.clear();
        var pending=cardService.authenticate(true,"contract01","123456".toCharArray());
        check("FE card service preserves MFA challenge rather than treating it as a login",pending.path("requiresMfa").asBoolean()&&!user.hasSession());
        var tokens=user.call("POST","/api/v1/auth/mfa/totp/verify",Map.of("transactionToken",pending.path("transactionToken").asText(),"code",totp(userSetup.path("secret").asText(),1)));
        user.session(tokens);
        check("FE completes card plus TOTP against real BE",user.call("GET","/api/v1/me",null).path("data").path("id").asText().equals(account));
        String readKey="";for(var permission:admin.call("GET","/api/v1/permissions",null).path("data"))if(permission.path("permission_code").asText().equals("key.read"))readKey=permission.path("id").asText();
        String viewerRole=admin.call("POST","/api/v1/roles",Map.of("code","CONTRACT_KEY_VIEWER","name","Contract viewer")).path("id").asText();
        admin.call("PUT","/api/v1/roles/"+viewerRole+"/permissions",Map.of("permissionIds",List.of(readKey)));
        String viewerAssignment=admin.call("POST","/api/v1/accounts/"+account+"/role-assignments",Map.of("roleId",viewerRole)).path("id").asText();
        check("FE gains a permission without replacing its JWT",!user.call("GET","/api/v1/signing-keys",null).path("data").isEmpty());
        boolean deniedRotate=false;try{user.call("POST","/api/v1/signing-keys/prepare",Map.of("alias","GENERATE"));}catch(IllegalStateException expected){deniedRotate=expected.getMessage().contains("403");}
        check("FE cannot turn read-only key permission into rotation",deniedRotate&&user.hasSession());
        admin.call("DELETE","/api/v1/role-assignments/"+viewerAssignment,null);
        boolean revokedPermission=false;try{user.call("GET","/api/v1/signing-keys",null);}catch(IllegalStateException expected){revokedPermission=expected.getMessage().contains("403");}
        check("FE sees permission revocation while its session stays active",revokedPermission&&user.hasSession());
        admin.call("POST","/api/v1/accounts/"+account+"/lock",Map.of());
        boolean locked=false;try{user.call("GET","/api/v1/me",null);}catch(IllegalStateException expected){locked=true;}
        check("Lock invalidates the real FE session and refresh state",locked&&!user.hasSession());
        admin.call("POST","/api/v1/accounts/"+account+"/unlock",Map.of());
        check("Unlock allows a fresh card challenge",cardService.authenticate(true,"contract01","123456".toCharArray()).path("requiresMfa").asBoolean());
        check("APDU private key was never exposed to an API",!transport.sawPrivateKeyField);
        var currentKey=admin.call("GET","/api/v1/signing-keys",null).path("data").get(0);
        String nextKey=admin.call("POST","/api/v1/signing-keys/prepare",Map.of("alias","GENERATE")).path("id").asText();
        check("FE reads active plus NEXT public JWK",admin.call("GET","/oauth2/jwks",null).path("keys").size()==2);
        admin.call("POST","/api/v1/signing-keys/rotate",Map.of("nextKeyId",nextKey,"overlapSeconds",600));
        check("FE stays authenticated through signing-key overlap",admin.call("GET","/api/v1/me",null).path("data").path("id").asText().equals(adminId));
        boolean protectedActive=false;try{admin.call("DELETE","/api/v1/signing-keys/"+nextKey,null);}catch(IllegalStateException expected){protectedActive=expected.getMessage().contains("409");}
        check("FE receives domain rejection when revoking active signing key",protectedActive&&admin.hasSession());

        var revocationChallenge=admin.call("POST","/api/v1/auth/smart-card/challenge",Map.of("username","contract01","cardIdentifier",identity.identifier()));
        byte[] revokedSignature;try(var t=card.open()){revokedSignature=new CardClient(t).sign(Base64.getDecoder().decode(revocationChallenge.path("payload").asText()),"123456".toCharArray());}
        String cardCredential=admin.call("GET","/api/v1/me/smart-cards?accountId="+account,null).path("data").get(0).path("id").asText();
        admin.call("DELETE","/api/v1/accounts/credentials/"+cardCredential,null);
        check("Revoked card remains visible in database",admin.call("GET","/api/v1/me/smart-cards?accountId="+account,null).path("data").get(0).path("status").asText().equals("REVOKED"));
        boolean revokedCard=false;try{admin.call("POST","/api/v1/auth/smart-card/verify",Map.of("challengeId",revocationChallenge.path("challengeId").asText(),"signature",Base64.getEncoder().encodeToString(revokedSignature)));}catch(IllegalStateException expected){revokedCard=expected.getMessage().contains("401");}
        check("Revoked card proof rejected even for previously issued challenge",revokedCard);

        admin.call("POST","/api/v1/logout",Map.of());
    }
    private void expect(MockMvc mvc,String method,String path,Object payload,String token,int expected)throws Exception {
        var request=MockMvcRequestBuilders.request(HttpMethod.valueOf(method),URI.create(path)).contentType("application/json");
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(payload!=null)request.content(json.writeValueAsString(payload));
        var response=mvc.perform(request).andReturn().getResponse();
        check("Validation "+method+" "+path+" -> "+expected,response.getStatus()==expected);
        if(expected>=400){var error=json.readTree(response.getContentAsString());check("Structured safe error",error.path("code").asText().matches("[A-Z0-9_]+")&&!error.toString().contains("stackTrace")&&!error.toString().contains("secret_hash"));}
    }
    private String totp(String base32,int offset){
        var output=new ByteArrayOutputStream();int buffer=0,bits=0;for(char c:base32.toCharArray()){buffer=(buffer<<5)|"ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);bits+=5;if(bits>=8){bits-=8;output.write((buffer>>bits)&255);}}
        return vn.kma.idp.credential.service.TotpAlgorithm.code(output.toByteArray(),java.time.Instant.now().getEpochSecond()/30+offset,6);
    }
    private void report()throws Exception{Path path=Path.of("target/e2e-test-results.json");Files.createDirectories(path.getParent());json.writerWithDefaultPrettyPrinter().writeValue(path.toFile(),Map.of("recordedAt",java.time.Instant.now().toString(),"backend","real Spring Security/controllers/services/repositories and H2","frontend","Java API/APDU compatibility layer: IdpApi, SmartCardAuthenticationService and CardClient; NOT React UI","simulation","HTTP transport via MockMvc; production DemoCardReader APDU emulator; NOT Oracle Simulator or physical card","cases",cases));}
    private final class MvcTransport implements ApiTransport {
        private final MockMvc mvc;private JsonNode lastTokens;private boolean sawPinField,sawPrivateKeyField,refreshWithoutBearer;
        MvcTransport(MockMvc mvc){this.mvc=mvc;}
        public ApiResponse send(HttpRequest request)throws Exception {
            var builder=MockMvcRequestBuilders.request(HttpMethod.valueOf(request.method()),request.uri());
            request.headers().map().forEach((key,values)->builder.header(key,values.toArray()));
            byte[] body=body(request);if(body.length>0){builder.content(body);JsonNode data=json.readTree(body);sawPinField|=data.has("pin");sawPrivateKeyField|=data.has("privateKey")||data.has("privateKeyPkcs8");}
            if(request.uri().getPath().equals("/api/v1/auth/refresh"))refreshWithoutBearer=request.headers().firstValue("Authorization").isEmpty();
            var response=mvc.perform(builder).andReturn().getResponse();String text=response.getContentAsString(StandardCharsets.UTF_8);
            if(response.getStatus()==200&&!text.isBlank()){var node=json.readTree(text);if(node.has("accessToken"))lastTokens=node;}
            return new ApiResponse(response.getStatus(),text);
        }
        private byte[] body(HttpRequest request)throws Exception {
            if(request.bodyPublisher().isEmpty())return new byte[0];var result=new CompletableFuture<byte[]>();var output=new ByteArrayOutputStream();
            request.bodyPublisher().get().subscribe(new Flow.Subscriber<ByteBuffer>(){
                public void onSubscribe(Flow.Subscription subscription){subscription.request(Long.MAX_VALUE);}
                public void onNext(ByteBuffer buffer){byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);output.writeBytes(bytes);}
                public void onError(Throwable error){result.completeExceptionally(error);}
                public void onComplete(){result.complete(output.toByteArray());}
            });return result.get(5,TimeUnit.SECONDS);
        }
    }
}
