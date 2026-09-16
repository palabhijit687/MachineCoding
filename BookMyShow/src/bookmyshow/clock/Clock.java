package bookmyshow.clock;

public interface Clock {

    long nowMillis();

    static Clock system() {
        return System::currentTimeMillis;
    }
}
