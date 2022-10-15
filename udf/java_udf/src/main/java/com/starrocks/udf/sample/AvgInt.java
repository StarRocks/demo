package com.starrocks.udf.sample;

/**
 * Define a complex function
 */
public class AvgInt {
    public static class State {
        int sum = 0;
        int counter = 0;
        public int serializeLength() { return 8; }
    }

    public AvgInt.State create() {
        return new AvgInt.State();
    }

    public void destroy(AvgInt.State state) {
    }

    public final void update(AvgInt.State state, Integer val) {
        if (val != null) {
            state.sum += val;
            state.counter += 1;
        }
    }

    public void serialize(AvgInt.State state, java.nio.ByteBuffer buff) {
        buff.putInt(state.sum);
        buff.putInt(state.counter);
    }

    public void merge(AvgInt.State state, java.nio.ByteBuffer buffer) {
        int valSum = buffer.getInt();
        int valCount = buffer.getInt();
        state.sum += valSum;
        state.counter += valCount;
    }

    public Float finalize(AvgInt.State state) {
        return (float)state.sum / state.counter;
    }
}
