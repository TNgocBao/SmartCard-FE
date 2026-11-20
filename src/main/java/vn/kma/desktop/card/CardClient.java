package vn.kma.desktop.card;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;
import javax.smartcardio.*;
public final class CardClient {
    public static final byte[] AID=HexFormat.of().parseHex("F04B4D4149445001");
    private final CardTransport transport;
    public CardClient(CardTransport transport) throws Exception {
        this.transport=transport;
        check(transport.transmit(new CommandAPDU(0,0xA4,4,0,AID,256)));
    }
    public record Identity(String identifier,String publicKeySpki,String fingerprint) {
    }
    public Identity identity() throws Exception {
        byte[] id=send(0x10,null,16),modulus=send(0x20,null,256),exponent=send(0x21,null,8);
        if(id.length!=16 || modulus.length!=256 || exponent.length<1 || exponent.length>8 || new BigInteger(1,modulus).bitLength()!=2048 || !new BigInteger(1,exponent).equals(BigInteger.valueOf(65537))) throw new CardOperationException("CARD_DATA_INVALID");
        var key=KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(new BigInteger(1,modulus),new BigInteger(1,exponent)));
        return new Identity(HexFormat.of().withUpperCase().formatHex(id),Base64.getEncoder().encodeToString(key.getEncoded()),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getEncoded())));
    }
    public byte[] sign(byte[] challenge,char[] pin) throws Exception {
        byte[] bytes=null;
        try{
            if(challenge==null||challenge.length<32||challenge.length>100)throw new CardOperationException("CARD_CHALLENGE_INVALID");
            bytes=pin(pin);send(0x40,bytes,0);byte[] signature=send(0x30,challenge,256);if(signature.length!=256)throw new CardOperationException("CARD_DATA_INVALID");return signature;
        }finally{wipe(bytes);wipe(pin);}
    }
    public void changePin(char[] current,char[] replacement) throws Exception {
        byte[] oldBytes=null,newBytes=null;
        try{oldBytes=pin(current);newBytes=pin(replacement);send(0x40,oldBytes,0);send(0x42,newBytes,0);}
        finally{wipe(oldBytes);wipe(newBytes);wipe(current);wipe(replacement);}
    }
    private static void wipe(byte[] value){if(value!=null)Arrays.fill(value,(byte)0);}
    private static void wipe(char[] value){if(value!=null)Arrays.fill(value,'\0');}
    private byte[] pin(char[] value){
        if(value==null||value.length<6||value.length>12)throw new CardOperationException("PIN_FORMAT_INVALID");
        byte[] result=new byte[value.length];
        for(int i=0;i<value.length;i++){
            if(value[i]<'0'||value[i]>'9'){wipe(result);throw new CardOperationException("PIN_FORMAT_INVALID");}
            result[i]=(byte)value[i];
        }
        return result;
    }
    private byte[] send(int ins,byte[] data,int le) throws Exception {
        CommandAPDU command=data==null ? new CommandAPDU(0x80,ins,0,0,le) : le==0 ? new CommandAPDU(0x80,ins,0,0,data) : new CommandAPDU(0x80,ins,0,0,data,le);
        return check(transport.transmit(command));
    }
    public static byte[] check(ResponseAPDU response) {
        if(response==null)throw new CardOperationException("CARD_NO_RESPONSE");
        int sw=response.getSW();
        if((sw&0xFFF0)==0x63C0){int tries=sw&15;throw new CardOperationException(tries==0?"PIN_BLOCKED":"PIN_INVALID",tries);}
        if(sw!=0x9000)throw new CardOperationException(switch(sw){case 0x6983 -> "PIN_BLOCKED";case 0x6982 -> "PIN_REQUIRED";case 0x6A82 -> "APPLET_NOT_FOUND";default -> "CARD_PROTOCOL_ERROR";});
        return response.getData();
    }
}
