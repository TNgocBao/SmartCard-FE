package vn.kma.desktop.card;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
public interface CardTransport extends AutoCloseable {
    ResponseAPDU transmit(CommandAPDU command) throws Exception;
    String description();
    void close() throws Exception;
}
