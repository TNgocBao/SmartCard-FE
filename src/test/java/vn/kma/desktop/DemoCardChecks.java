package vn.kma.desktop;

import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import javax.smartcardio.*;
import vn.kma.desktop.card.*;

/** Exercises the production software reader and production APDU client. */
public final class DemoCardChecks {
    private static int checks;
    private static void check(String label,boolean ok){if(!ok)throw new AssertionError(label);checks++;}
    private static void error(String expected,Checked action)throws Exception{
        try{action.run();throw new AssertionError("Expected "+expected);}catch(CardOperationException e){check(expected,e.code().equals(expected));}
    }
    private interface Checked{void run()throws Exception;}
    public static void main(String[] args)throws Exception{
        var reader=new DemoCardReader();
        error("CARD_NOT_AVAILABLE",reader::open);reader.create();
        error("APPLET_NOT_FOUND",()->{try(var t=reader.open()){new CardClient(t);}});
        reader.install();String id;String publicKey;
        try(var t=reader.open()){
            var client=new CardClient(t);var identity=client.identity();id=identity.identifier();publicKey=identity.publicKeySpki();
            check("RSA public key only",identity.fingerprint().length()==64&&id.length()==32);
            byte[] challenge=new byte[57];new SecureRandom().nextBytes(challenge);
            check("PIN required",t.transmit(new CommandAPDU(0x80,0x30,0,0,challenge,256)).getSW()==0x6982);
            char[] pin="123456".toCharArray();byte[] signature=client.sign(challenge,pin);
            check("PIN wiped",Arrays.equals(pin,new char[6]));
            var verify=Signature.getInstance("SHA256withRSA");verify.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey))));verify.update(challenge);
            check("RSA signature verifies",verify.verify(signature));
            check("PIN reset after signing",t.transmit(new CommandAPDU(0x80,0x30,0,0,challenge,256)).getSW()==0x6982);
            client.changePin("123456".toCharArray(),"654321".toCharArray());
            error("PIN_INVALID",()->client.verifyPin("123456".toCharArray()));client.verifyPin("654321".toCharArray());
        }
        reader.install();
        try(var t=reader.open()){var c=new CardClient(t);check("Repeat install preserves identity",c.identity().publicKeySpki().equals(publicKey));c.verifyPin("654321".toCharArray());}
        reader.remove();error("CARD_NOT_AVAILABLE",reader::open);reader.insert(id);
        try(var t=reader.open()){
            var c=new CardClient(t);
            for(int i=0;i<4;i++)error("PIN_INVALID",()->c.verifyPin("000000".toCharArray()));
            error("PIN_BLOCKED",()->c.verifyPin("000000".toCharArray()));
            error("PIN_BLOCKED",()->c.verifyPin("654321".toCharArray()));
        }
        reader.install();try(var t=reader.open()){var c=new CardClient(t);error("PIN_BLOCKED",()->c.verifyPin("123456".toCharArray()));}
        reader.create();reader.install();try(var t=reader.open()){check("New card has a distinct key",!new CardClient(t).identity().publicKeySpki().equals(publicKey));}
        String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(reader.status());
        check("Trace excludes PIN and key material",!json.contains("654321")&&!json.contains("private")&&!json.contains(publicKey));
        System.out.println("DEMO CARD CHECKS PASSED: "+checks);
    }
}
