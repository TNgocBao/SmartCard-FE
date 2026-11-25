package vn.kma.desktop;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import vn.kma.desktop.card.*;

/** Loopback-only web host, fixed upstream proxy and PC/SC bridge. No UI or token storage. */
public final class WebApplication {
    private static final ObjectMapper JSON = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
        .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(262144).build())
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Object CARD_LOCK = new Object();
    private static final DemoCardReader DEMO = new DemoCardReader();
    private static final boolean DEMO_ENABLED = "true".equalsIgnoreCase(System.getenv("IDP_DEMO_CARD"));
    private static final class UpstreamClient {
        private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    private static URI backend;
    private static Path web;
    public static void main(String[] args) throws Exception {
        String base = System.getenv().getOrDefault("IDP_API_BASE_URL", "http://127.0.0.1:8080").replaceAll("/$", "");
        backend = URI.create(base);
        if (backend.getHost() == null || backend.getUserInfo() != null || backend.getQuery() != null || backend.getFragment() != null || !("https".equals(backend.getScheme()) || ("http".equals(backend.getScheme()) && Set.of("localhost", "127.0.0.1").contains(backend.getHost())))) throw new IllegalArgumentException("Invalid IDP_API_BASE_URL");
        web = Path.of("dist").toAbsolutePath().normalize();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8787), 32);
        server.createContext("/", WebApplication::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        server.start();
        System.out.println("KMA Identity web: http://127.0.0.1:8787 | development: http://127.0.0.1:5173");
    }
    private static void handle(HttpExchange x) throws java.io.IOException {
        try {
            String host = x.getRequestHeaders().getFirst("Host");
            String origin = x.getRequestHeaders().getFirst("Origin");
            if (!Set.of("127.0.0.1:8787", "localhost:8787", "127.0.0.1:5173", "localhost:5173").contains(Objects.toString(host,""))) { reply(x,403,Map.of("code","ACCESS_DENIED")); return; }
            if (origin != null && !Set.of("http://127.0.0.1:8787", "http://localhost:8787", "http://127.0.0.1:5173", "http://localhost:5173").contains(origin)) { reply(x,403,Map.of("code","ACCESS_DENIED")); return; }
            String path = x.getRequestURI().getPath();
            if (!Set.of("GET","POST","PUT","PATCH","DELETE").contains(x.getRequestMethod())) { reply(x,405,Map.of("code","METHOD_NOT_ALLOWED")); return; }
            if (!x.getRequestMethod().equals("GET") && (origin == null || !Objects.toString(x.getRequestHeaders().getFirst("Content-Type"), "").startsWith("application/json"))) { reply(x,403,Map.of("code","ACCESS_DENIED")); return; }
            x.getResponseHeaders().set("Cache-Control", "no-store");
            x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            x.getResponseHeaders().set("X-Frame-Options", "DENY");
            if (path.startsWith("/api/") || path.equals("/oauth2/jwks")) { proxy(x); return; }
            if (path.startsWith("/bridge/")) { card(x,path); return; }
            if (!x.getRequestMethod().equals("GET")) { reply(x,405,Map.of("code","METHOD_NOT_ALLOWED")); return; }
            Path file = web.resolve(path.substring(1)).normalize();
            if (!file.startsWith(web)) { reply(x,403,Map.of("code","ACCESS_DENIED")); return; }
            if (!Files.isRegularFile(file)) file = web.resolve("index.html");
            if (!Files.isRegularFile(file)) { reply(x,503,Map.of("code","WEB_BUILD_REQUIRED")); return; }
            String name = file.getFileName().toString();
            String type = name.endsWith(".js") ? "text/javascript" : name.endsWith(".css") ? "text/css" : name.endsWith(".svg") ? "image/svg+xml" : "text/html";
            x.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");
            x.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
            byte[] bytes = Files.readAllBytes(file); x.sendResponseHeaders(200,bytes.length); x.getResponseBody().write(bytes);
        } catch (CardOperationException e) { reply(x,400,Map.of("code",e.code(),"remainingTries",e.remainingTries())); }
        catch (com.fasterxml.jackson.core.JacksonException e) { reply(x,400,Map.of("code","VALIDATION_ERROR")); }
        catch (IllegalArgumentException e) { reply(x,400,Map.of("code","VALIDATION_ERROR")); }
        catch (Exception e) { reply(x,503,Map.of("code",x.getRequestURI().getPath().startsWith("/bridge/")?"CARD_CONNECTION_FAILED":"SERVICE_UNAVAILABLE")); }
        finally { x.close(); }
    }
    private static byte[] body(HttpExchange x) throws Exception {
        byte[] bytes = x.getRequestBody().readNBytes(262145);
        if (bytes.length > 262144) throw new IllegalArgumentException();
        return bytes;
    }
    private static void proxy(HttpExchange x) throws Exception {
        // Fixed authority; never accept a target URL from the browser or follow redirects.
        URI target = URI.create(backend.toString()+x.getRequestURI().toASCIIString());
        var request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(25)).header("Accept","application/json").header("Content-Type","application/json");
        String authorization = x.getRequestHeaders().getFirst("Authorization");
        if (authorization != null) request.header("Authorization",authorization);
        var response = UpstreamClient.HTTP.send(request.method(x.getRequestMethod(),HttpRequest.BodyPublishers.ofByteArray(body(x))).build(),HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;
        try (var stream = response.body()) { bytes = stream.readNBytes(2097153); }
        if (bytes.length>2097152) { reply(x,502,Map.of("code","RESPONSE_TOO_LARGE")); return; }
        x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        if (bytes.length==0) x.sendResponseHeaders(response.statusCode(),-1);
        else { x.sendResponseHeaders(response.statusCode(),bytes.length); x.getResponseBody().write(bytes); }
    }
    private static void card(HttpExchange x,String path) throws Exception {
        if (path.equals("/bridge/status") && x.getRequestMethod().equals("GET")) { reply(x,200,Map.of("status","UP","demoEnabled",DEMO_ENABLED,"oracle",OracleAppletInstaller.status())); return; }
        if (path.equals("/bridge/demo/status") && x.getRequestMethod().equals("GET")) {
            if(!DEMO_ENABLED){reply(x,403,Map.of("code","DEMO_DISABLED"));return;}
            reply(x,200,DEMO.status());return;
        }
        if (!Set.of("/bridge/card/read","/bridge/card/sign","/bridge/card/pin","/bridge/card/verify-pin","/bridge/oracle/install","/bridge/demo/create","/bridge/demo/install","/bridge/demo/insert","/bridge/demo/remove").contains(path)) { reply(x,404,Map.of("code","RESOURCE_NOT_FOUND")); return; }
        if (!x.getRequestMethod().equals("POST")) { reply(x,405,Map.of("code","METHOD_NOT_ALLOWED")); return; }
        JsonNode data = JSON.readTree(body(x));
        if (data == null || !data.isObject()) throw new IllegalArgumentException();
        validateCardRequest(path,data);
        if(path.startsWith("/bridge/demo/") || path.equals("/bridge/oracle/install")) {
            if(!DEMO_ENABLED){reply(x,403,Map.of("code","DEMO_DISABLED"));return;}
            synchronized(CARD_LOCK) {
                switch(path) {
                    case "/bridge/demo/create" -> DEMO.create();
                    case "/bridge/demo/install" -> DEMO.install();
                    case "/bridge/demo/insert" -> DEMO.insert(data.path("cardIdentifier").asText());
                    case "/bridge/demo/remove" -> DEMO.remove();
                    case "/bridge/oracle/install" -> OracleAppletInstaller.install();
                    default -> throw new IllegalArgumentException();
                }
                reply(x,200,path.startsWith("/bridge/demo/")?DEMO.status():Map.of("status","INSTALLED"));return;
            }
        }
        String mode = data.path("transport").asText();
        if (!Set.of("SOFTWARE","SIMULATOR","PCSC").contains(mode)) throw new IllegalArgumentException();
        if(mode.equals("SOFTWARE")&&!DEMO_ENABLED){reply(x,403,Map.of("code","DEMO_DISABLED"));return;}
        synchronized (CARD_LOCK) {
            try (var transport = mode.equals("SOFTWARE")?DEMO.open():new PcscTransport(mode.equals("SIMULATOR"),"127.0.0.1",9025)) {
                var client = new CardClient(transport);
                var identity = client.identity();
                if (path.endsWith("/read")) { reply(x,200,identity); return; }
                if (!identity.identifier().equals(data.path("cardIdentifier").asText())) throw new CardOperationException("CARD_CHANGED");
                char[] pin = data.path("pin").asText().toCharArray();
                try {
                    if (path.endsWith("/verify-pin")) {client.verifyPin(pin);reply(x,200,Map.of("status","OK"));}
                    else if (path.endsWith("/sign")) reply(x,200,Map.of("signature",Base64.getEncoder().encodeToString(client.sign(Base64.getDecoder().decode(data.path("payload").asText()),pin))));
                    else { client.changePin(pin,data.path("newPin").asText().toCharArray()); reply(x,200,Map.of("status","OK")); }
                } finally { Arrays.fill(pin,'\0'); }
            }
        }
    }
    private static void validateCardRequest(String path,JsonNode data) {
        Set<String> required;
        if(path.equals("/bridge/demo/insert"))required=Set.of("cardIdentifier");
        else if(path.startsWith("/bridge/demo/")||path.equals("/bridge/oracle/install"))required=Set.of();
        else if(path.endsWith("/read"))required=Set.of("transport");
        else if(path.endsWith("/sign"))required=Set.of("transport","cardIdentifier","pin","payload");
        else if(path.endsWith("/pin"))required=Set.of("transport","cardIdentifier","pin","newPin");
        else required=Set.of("transport","cardIdentifier","pin");
        var names=new HashSet<String>();data.fieldNames().forEachRemaining(names::add);
        if(!names.equals(required))throw new IllegalArgumentException();
        for(String name:required)if(!data.path(name).isTextual()||data.path(name).asText().isBlank())throw new IllegalArgumentException();
        if(data.has("cardIdentifier")&&!data.path("cardIdentifier").asText().matches("[0-9A-F]{32}"))throw new IllegalArgumentException();
        for(String field:List.of("pin","newPin"))if(data.has(field)&&!data.path(field).asText().matches("[0-9]{6,12}"))throw new CardOperationException("PIN_FORMAT_INVALID");
        if(data.has("payload")) {
            String text=data.path("payload").asText();
            if(text.length()>136)throw new CardOperationException("CARD_CHALLENGE_INVALID");
            byte[] bytes;
            try{bytes=Base64.getDecoder().decode(text);}catch(IllegalArgumentException e){throw new CardOperationException("CARD_CHALLENGE_INVALID");}
            if(bytes.length<32||bytes.length>100)throw new CardOperationException("CARD_CHALLENGE_INVALID");
        }
    }
    private static void reply(HttpExchange x,int status,Object data) throws java.io.IOException {
        byte[] bytes = JSON.writeValueAsBytes(data);
        x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        x.getResponseHeaders().set("Cache-Control","no-store");
        x.sendResponseHeaders(status,bytes.length); x.getResponseBody().write(bytes);
    }
}
