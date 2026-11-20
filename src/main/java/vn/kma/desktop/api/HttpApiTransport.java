package vn.kma.desktop.api;

import java.net.http.*;
import java.time.Duration;

/** Lazily creates the network stack so the desktop can open while offline. */
public final class HttpApiTransport implements ApiTransport {
    private HttpClient client;
    private synchronized HttpClient client(){if(client==null)client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();return client;}
    public ApiResponse send(HttpRequest request) throws Exception {
        var response=client().send(request,new BoundedJsonBodyHandler(1024*1024));
        return new ApiResponse(response.statusCode(),response.body());
    }
}
