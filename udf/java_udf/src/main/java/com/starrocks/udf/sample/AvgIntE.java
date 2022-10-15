package com.starrocks.udf.sample;

/**there is an error about the serializeLength() which should return 8
 * It will cause exception
 */
public class AvgIntE {
    public static class State {
        int sum = 0;
        int counter = 0;
        public int serializeLength() { return 4; }
    }

    public AvgIntE.State create() {
        return new AvgIntE.State();
    }

    public void destroy(AvgIntE.State state) {
    }

    public final void update(AvgIntE.State state, Integer val) {
        if (val != null) {
            state.sum += val;
            state.counter += 1;
        }
    }

    public void serialize(AvgIntE.State state, java.nio.ByteBuffer buff) {
        buff.putInt(state.sum);
        buff.putInt(state.counter);
    }

    public void merge(AvgIntE.State state, java.nio.ByteBuffer buffer) {
        int valSum = buffer.getInt();
        int valCount = buffer.getInt();
        state.sum += valSum;
        state.counter += valCount;
    }

    public Float finalize(AvgIntE.State state) {
        return (float)state.sum / state.counter;
    }
}
