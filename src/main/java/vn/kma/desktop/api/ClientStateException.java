package vn.kma.desktop.api;
public final class ClientStateException extends IllegalStateException {
    private final String code;
    public ClientStateException(String code){super(code);this.code=code;}
    public String code(){return code;}
}
