package vn.kma.desktop.card;

/** Stable card error metadata; PINs, challenges and key material are never exception messages. */
public final class CardOperationException extends IllegalStateException {
    private final String code;
    private final int remainingTries;
    public CardOperationException(String code){this(code,-1);}
    public CardOperationException(String code,int remainingTries){super(code);this.code=code;this.remainingTries=remainingTries;}
    public String code(){return code;}
    public int remainingTries(){return remainingTries;}
}
