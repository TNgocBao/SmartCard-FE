package vn.kma.desktop.card;

@FunctionalInterface
public interface CardTransportFactory {
    CardTransport open(boolean simulator) throws Exception;
}
