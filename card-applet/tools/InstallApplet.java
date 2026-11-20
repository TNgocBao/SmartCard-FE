import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import javax.smartcardio.*;
import com.oracle.javacard.ams.*;
import com.oracle.javacard.ams.config.*;
/** Installation only. Never uninstall automatically; identity keys must survive desktop restarts. */
public final class InstallApplet {
    public static void main(String[] args) throws Exception {
        Properties properties=new Properties();
        try(var input=Files.newInputStream(Path.of(args[1]))){
            properties.load(input);
        }
        var service=AMServiceFactory.getInstance("GP2.2");
        service.setProperties(properties);
        if(java.security.Security.getProvider("SocketCardTerminalProvider")==null) {
            java.util.ServiceLoader.load(java.security.Provider.class).stream()
                .map(java.util.ServiceLoader.Provider::get)
                .filter(provider -> provider.getName().equals("SocketCardTerminalProvider"))
                .findFirst().ifPresent(java.security.Security::addProvider);
        }
        var factory=TerminalFactory.getInstance("SocketCardTerminalFactoryType",List.of(new InetSocketAddress("localhost",9025)),"SocketCardTerminalProvider");
        var terminal=factory.terminals().list().getFirst();
        if(!terminal.waitForCardPresent(5000))throw new IllegalStateException("Start Oracle simulator first");
        Card card=terminal.connect("*");
        try {
            var channel=card.getBasicChannel();
            var select=channel.transmit(new CommandAPDU(0,0xA4,4,0,HexFormat.of().parseHex("F04B4D4149445001"),256));
            if(select.getSW()==0x9000){
                System.out.println("Applet already installed; retained existing card identity.");
                return;
            }
            service.openSession("aid:A000000151000000").load("aid:F04B4D41494450",CAPFile.from(args[0]).getBytes()).install("aid:F04B4D41494450","aid:F04B4D4149445001","aid:F04B4D4149445001").close().run(channel);
            if(channel.transmit(new CommandAPDU(0,0xA4,4,0,HexFormat.of().parseHex("F04B4D4149445001"),256)).getSW()!=0x9000)throw new IllegalStateException("Applet selection failed after install");
            System.out.println("Identity applet installed. Initial lab PIN: 123456. Change it in the desktop app.");
        }
        finally{
            card.disconnect(false);
        }
    }
}
