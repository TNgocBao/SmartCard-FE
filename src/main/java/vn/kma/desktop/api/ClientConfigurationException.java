package vn.kma.desktop.api;
public final class ClientConfigurationException extends IllegalArgumentException {
    private final String code;
    public ClientConfigurationException(String code){super(code);this.code=code;}
    public String code(){return code;}
}
