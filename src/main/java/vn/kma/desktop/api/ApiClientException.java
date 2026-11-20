package vn.kma.desktop.api;

/** Sanitized HTTP failure. Does not retain the raw server body. */
public final class ApiClientException extends IllegalStateException {
    private final int status;
    private final String code,requestId;
    private final java.util.List<String> fields;
    public ApiClientException(int status,String code,String requestId){
        this(status,code,requestId,java.util.List.of());
    }
    public ApiClientException(int status,String code,String requestId,java.util.List<String> fields){
        super("HTTP "+status+" · "+safeCode(code));this.status=status;this.code=safeCode(code);
        this.requestId=requestId!=null&&requestId.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")?requestId:"";
        this.fields=fields.stream().filter(field->field!=null&&field.matches("[A-Za-z][A-Za-z0-9_]{0,63}")).distinct().limit(5).toList();
    }
    private static String safeCode(String code){return code!=null&&code.matches("[A-Z][A-Z0-9_]{1,79}")?code:"REQUEST_REJECTED";}
    public int status(){return status;}
    public String code(){return code;}
    public String requestId(){return requestId;}
    public java.util.List<String> fields(){return fields;}
}
