package vn.kma.desktop.service;
import java.util.*;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.JsonNode;
import vn.kma.desktop.api.IdpApi;
import vn.kma.desktop.card.*;
/** Executes the card authentication use case without a graphical UI. */
public final class SmartCardAuthenticationService {
    private final Supplier<IdpApi> api;
    private final CardTransportFactory transports;
    public SmartCardAuthenticationService(Supplier<IdpApi> api){
        this(api,simulator->new PcscTransport(simulator,"127.0.0.1",9025));
    }
    public SmartCardAuthenticationService(Supplier<IdpApi> api,CardTransportFactory transports){
        this.api=api;
        this.transports=transports;
    }
    public CardClient.Identity read(boolean simulator)throws Exception {
        try(var transport=transports.open(simulator)){
            return new CardClient(transport).identity();
        }
    }
    public JsonNode authenticate(boolean simulator,String username,char[] pin)throws Exception {return authenticate(api.get(),simulator,username,pin);}
    public JsonNode authenticate(IdpApi target,boolean simulator,String username,char[] pin)throws Exception {
        long version=target.sessionVersion();
        try(var transport=transports.open(simulator)) {
            var card=new CardClient(transport);
            var identity=card.identity();
            target.requireVersion(version);
            var challenge=target.call("POST","/api/v1/auth/smart-card/challenge",Map.of("username",username,"cardIdentifier",identity.identifier()));
            byte[] signature=card.sign(Base64.getDecoder().decode(challenge.path("payload").asText()),pin);
            target.requireVersion(version);
            var tokens=target.call("POST","/api/v1/auth/smart-card/verify",Map.of("challengeId",challenge.path("challengeId").asText(),"signature",Base64.getEncoder().encodeToString(signature)));
            if(tokens.has("requiresMfa")||tokens.has("requiresEnrollment"))return tokens;
            target.session(tokens,version);
            return target.call("GET","/api/v1/me",null);
        }
        finally {
            if(pin!=null)Arrays.fill(pin,'\0');
        }
    }
    public void changePin(boolean simulator,char[] old,char[] next)throws Exception {
        try(var transport=transports.open(simulator)){
            new CardClient(transport).changePin(old,next);
        }
        finally{
            if(old!=null)Arrays.fill(old,'\0');
            if(next!=null)Arrays.fill(next,'\0');
        }
    }
}
