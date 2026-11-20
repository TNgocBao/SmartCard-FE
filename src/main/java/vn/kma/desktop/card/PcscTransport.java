package vn.kma.desktop.card;
import java.net.InetSocketAddress;
import java.util.List;
import javax.smartcardio.*;
/** Same APDU implementation for Oracle simulator and a physical PC/SC reader. */
public final class PcscTransport implements CardTransport {
    private final Card card;
    private final String description;
    public PcscTransport(boolean simulator,String host,int port) throws Exception {
        if(simulator && !List.of("127.0.0.1","localhost").contains(host)) throw new IllegalArgumentException("Simulator must be local");
        if(simulator && java.security.Security.getProvider("SocketCardTerminalProvider")==null) {
            java.util.ServiceLoader.load(java.security.Provider.class).stream()
                .map(java.util.ServiceLoader.Provider::get)
                .filter(provider -> provider.getName().equals("SocketCardTerminalProvider"))
                .findFirst().ifPresent(java.security.Security::addProvider);
        }
        TerminalFactory factory=simulator ? TerminalFactory.getInstance("SocketCardTerminalFactoryType",List.of(new InetSocketAddress(host,port)),"SocketCardTerminalProvider") : TerminalFactory.getDefault();
        var terminals=factory.terminals().list();
        if(terminals.isEmpty()) throw new CardOperationException("CARD_NOT_AVAILABLE");
        CardTerminal terminal=terminals.stream().filter(t -> {
            try {
                return t.isCardPresent();
            }
            catch(CardException e) {
                return false;
            }
        }
        ).findFirst().orElse(terminals.getFirst());
        if(!terminal.waitForCardPresent(3000)) throw new CardOperationException("CARD_NOT_AVAILABLE");
        card=terminal.connect("*");
        try{card.beginExclusive();}catch(CardException e){try{card.disconnect(false);}catch(CardException close){e.addSuppressed(close);}throw e;}
        description=(simulator?"SIMULATOR · ":"PC/SC · ")+terminal.getName();
    }
    public ResponseAPDU transmit(CommandAPDU command) throws CardException {
        return card.getBasicChannel().transmit(command);
    }
    public String description() {
        return description;
    }
    public void close() throws CardException {
        try{card.endExclusive();}finally{card.disconnect(false);}
    }
}
