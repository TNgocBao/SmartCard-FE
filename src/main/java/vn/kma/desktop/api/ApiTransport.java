package vn.kma.desktop.api;

import java.net.http.HttpRequest;

@FunctionalInterface
public interface ApiTransport {
    ApiResponse send(HttpRequest request) throws Exception;
}
