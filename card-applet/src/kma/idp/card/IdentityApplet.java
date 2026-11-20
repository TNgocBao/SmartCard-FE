package kma.idp.card;
import javacard.framework.*;
import javacard.security.*;
/** Lab protocol v1. Do not deploy a lab PIN or unverified firmware on production cards. */
public final class IdentityApplet extends Applet {
    private final KeyPair keys;
    private final Signature signer;
    private final OwnerPIN pin;
    private final byte[] cardId;
    private final byte[] work;
    private IdentityApplet() {
        keys=new KeyPair(KeyPair.ALG_RSA_CRT,KeyBuilder.LENGTH_RSA_2048);
        keys.genKeyPair();
        signer=Signature.getInstance(Signature.ALG_RSA_SHA_256_PKCS1,false);
        pin=new OwnerPIN((byte)5,(byte)12);
        byte[] initial={
            (byte)'1',(byte)'2',(byte)'3',(byte)'4',(byte)'5',(byte)'6'
        }
        ;
        pin.update(initial,(short)0,(byte)initial.length);
        cardId=new byte[16];
        RandomData.getInstance(RandomData.ALG_SECURE_RANDOM).generateData(cardId,(short)0,(short)16);
        work=JCSystem.makeTransientByteArray((short)256,JCSystem.CLEAR_ON_DESELECT);
        register();
    }
    public static void install(byte[] b,short offset,byte length) {
        new IdentityApplet();
    }
    public void deselect() {
        pin.reset();
    }
    public void process(APDU apdu) {
        if(selectingApplet())return;
        byte[] buffer=apdu.getBuffer();
        if(buffer[ISO7816.OFFSET_CLA]!=(byte)0x80)ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        if(buffer[ISO7816.OFFSET_P1]!=0||buffer[ISO7816.OFFSET_P2]!=0)ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        short length;
        switch(buffer[ISO7816.OFFSET_INS]) {
            case (byte)0x10: apdu.setOutgoing();
            apdu.setOutgoingLength((short)16);
            apdu.sendBytesLong(cardId,(short)0,(short)16);
            break;
            case (byte)0x20: length=((RSAPublicKey)keys.getPublic()).getModulus(work,(short)0);
            send(apdu,length);
            break;
            case (byte)0x21: length=((RSAPublicKey)keys.getPublic()).getExponent(work,(short)0);
            send(apdu,length);
            break;
            case (byte)0x40:
            length=receive(apdu);
            if(length<6||length>12)ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            if(!pin.check(work,(short)0,(byte)length))ISOException.throwIt((short)(0x63C0|pin.getTriesRemaining()));
            Util.arrayFillNonAtomic(work,(short)0,(short)256,(byte)0);
            break;
            case (byte)0x42:
            if(!pin.isValidated())ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            length=receive(apdu);
            if(length<6||length>12)ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            for(short i=0;i<length;i++)if(work[i]<'0'||work[i]>'9')ISOException.throwIt(ISO7816.SW_WRONG_DATA);
            pin.update(work,(short)0,(byte)length);
            pin.reset();
            Util.arrayFillNonAtomic(work,(short)0,(short)256,(byte)0);
            break;
            case (byte)0x30:
            if(!pin.isValidated())ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            length=receive(apdu);
            if(length<32||length>100)ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            signer.init(keys.getPrivate(),Signature.MODE_SIGN);
            // update before writing signature into the same temporary buffer.
            signer.update(work,(short)0,length);
            length=signer.sign(work,(short)0,(short)0,work,(short)0);
            pin.reset();
            send(apdu,length);
            break;
            default:ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
    private short receive(APDU apdu){
        short read=apdu.setIncomingAndReceive(),total=0;
        short incoming=apdu.getIncomingLength();
        if(incoming>100)ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        while(read>0){
            if((short)(total+read)>100)ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            Util.arrayCopyNonAtomic(apdu.getBuffer(),apdu.getOffsetCdata(),work,total,read);
            total+=read;
            read=apdu.receiveBytes(apdu.getOffsetCdata());
        }
        if(total!=incoming)ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        return total;
    }
    private void send(APDU apdu,short length){
        apdu.setOutgoing();
        apdu.setOutgoingLength(length);
        apdu.sendBytesLong(work,(short)0,length);
    }
}
