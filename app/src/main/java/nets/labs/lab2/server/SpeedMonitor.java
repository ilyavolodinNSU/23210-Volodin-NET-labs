package nets.labs.lab2.server;

import java.util.concurrent.atomic.AtomicLong;

public class SpeedMonitor implements Runnable {
    private final AtomicLong totalBytes;
    private volatile boolean isCompleted;
    private final long startTimestamp;
    private final String fileName;
    
    public SpeedMonitor(AtomicLong totalBytes, boolean isCompleted, long startTimestamp, String fileName) {
        this.totalBytes = totalBytes;
        this.isCompleted = isCompleted;
        this.startTimestamp = startTimestamp;
        this.fileName = fileName;
    }
    
    @Override
    public void run() {
        long lastSpeedPrintTime = System.currentTimeMillis();
        long lastSpeedPrintBytes = 0;

        while (!isCompleted) {
            long currentTime = System.currentTimeMillis();
            long currentTotal = totalBytes.get();
            long elapsed = currentTime - lastSpeedPrintTime;
            long bytesSinceLast = currentTotal - lastSpeedPrintBytes;

            double instantaneousMBps = (elapsed == 0) ? 0.0 : 
                (bytesSinceLast / (elapsed / 1000.0)) / Constants.BYTES_TO_MEGABYTES_DIVISOR;
            double averageMBps = (currentTime - startTimestamp) == 0 ? 0.0 : 
                (currentTotal / ((currentTime - startTimestamp) / 1000.0)) / Constants.BYTES_TO_MEGABYTES_DIVISOR;

            System.out.printf("File '%s': instantaneous %.2f MB/s, average %.2f MB/s%n",
                fileName, instantaneousMBps, averageMBps);

            lastSpeedPrintTime = currentTime;
            lastSpeedPrintBytes = currentTotal;

            try {
                Thread.sleep(Constants.SPEED_MONITOR_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}