package vn.kma.desktop.card;

import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import javax.smartcardio.*;

/** In-memory APDU emulator for local demonstrations, never a hardware security boundary. */
public final class DemoCardReader {
    private final Map<String, VirtualCard> cards = new LinkedHashMap<>();
    private String inserted;
    private final Deque<Map<String,Object>> trace = new ArrayDeque<>();

    public synchronized Map<String,Object> status() {
        return Map.of("cards", cards.values().stream().map(c -> Map.of(
            "identifier", c.id, "installed", c.keys != null, "remainingTries", c.tries,
            "inserted", c.id.equals(inserted))).toList(), "inserted", Objects.toString(inserted,""),
            "trace", List.copyOf(trace), "persistent", false);
    }
    public synchronized void create() {
        if (cards.size() >= 12) throw new CardOperationException("DEMO_CARD_LIMIT");
        var card = new VirtualCard(); cards.put(card.id, card); inserted = card.id;
    }
    public synchronized void insert(String id) {
        if (!cards.containsKey(id)) throw new CardOperationException("CARD_NOT_AVAILABLE");
        inserted = id;
    }
    public synchronized void remove() { inserted = null; }
    public synchronized void install() throws GeneralSecurityException {
        var card = current();
        if (card.keys != null) return; // Preserve identity and PIN on repeat installation.
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        card.keys = generator.generateKeyPair();
        card.pin = digest("123456".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
    private static byte[] digest(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    private VirtualCard current() {
        var card = cards.get(inserted);
        if (card == null) throw new CardOperationException("CARD_NOT_AVAILABLE");
        return card;
    }
    public synchronized CardTransport open() {
        var card = current();
        return new CardTransport() {
            private boolean selected, validated, closed;
            public String description() { return "Software APDU emulator"; }
            public void close() { closed = true; validated = false; }
            public ResponseAPDU transmit(CommandAPDU command) throws Exception {
                synchronized (DemoCardReader.this) {
                    if (closed || !card.id.equals(inserted)) throw new CardOperationException("CARD_NOT_AVAILABLE");
                    var response = execute(command);
                    if (trace.size() == 60) trace.removeFirst();
                    trace.addLast(Map.of("time", java.time.Instant.now().toString(),
                        "command", String.format("%02X %02X %02X %02X",command.getCLA(),command.getINS(),command.getP1(),command.getP2()),
                        "inputBytes", command.getNc(), "outputBytes", response.getData().length,
                        "sw", String.format("%04X",response.getSW())));
                    return response;
                }
            }
            private ResponseAPDU execute(CommandAPDU c) throws Exception {
                if(c.getCLA()==0 && c.getINS()==0xA4) {
                    validated=false; selected=card.keys!=null && Arrays.equals(c.getData(),CardClient.AID);
                    return reply(null,selected?0x9000:0x6A82);
                }
                if(!selected) return reply(null,0x6999);
                if(c.getCLA()!=0x80)return reply(null,0x6E00);
                if(c.getP1()!=0||c.getP2()!=0)return reply(null,0x6A86);
                byte[] data=c.getData();
                switch(c.getINS()) {
                    case 0x10: return reply(HexFormat.of().parseHex(card.id),0x9000);
                    case 0x20: return reply(unsigned(((RSAPublicKey)card.keys.getPublic()).getModulus().toByteArray()),0x9000);
                    case 0x21: return reply(unsigned(((RSAPublicKey)card.keys.getPublic()).getPublicExponent().toByteArray()),0x9000);
                    case 0x40:
                        validated=false;
                        if(data.length<6||data.length>12)return reply(null,0x6700);
                        if(card.tries==0)return reply(null,0x63C0);
                        if(!MessageDigest.isEqual(card.pin,digest(data))){card.tries--;return reply(null,0x63C0|card.tries);}
                        card.tries=5; validated=true; return reply(null,0x9000);
                    case 0x42:
                        if(!validated)return reply(null,0x6982);
                        if(data.length<6||data.length>12)return reply(null,0x6700);
                        for(byte b:data)if(b<'0'||b>'9')return reply(null,0x6A80);
                        card.pin=digest(data);card.tries=5;validated=false;return reply(null,0x9000);
                    case 0x30:
                        if(!validated)return reply(null,0x6982);
                        if(data.length<32||data.length>100)return reply(null,0x6700);
                        var signer=Signature.getInstance("SHA256withRSA");
                        signer.initSign(card.keys.getPrivate());signer.update(data);
                        validated=false;return reply(signer.sign(),0x9000);
                    default:return reply(null,0x6D00);
                }
            }
        };
    }
    private static byte[] unsigned(byte[] value){return value.length>1&&value[0]==0?Arrays.copyOfRange(value,1,value.length):value;}
    private static ResponseAPDU reply(byte[] value,int sw) {
        byte[] result=Arrays.copyOf(value==null?new byte[0]:value,(value==null?0:value.length)+2);
        result[result.length-2]=(byte)(sw>>8);result[result.length-1]=(byte)sw;
        return new ResponseAPDU(result);
    }
    private static final class VirtualCard {
        final String id; KeyPair keys; byte[] pin; int tries=5;
        VirtualCard(){byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);id=HexFormat.of().withUpperCase().formatHex(bytes);}
    }
}
