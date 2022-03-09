package com.example.sistemidigitali.model;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat;

/**
 * Source: https://github.com/shubham0204/Realtime_MiDaS_Depth_Estimation_Android/blob/65cd321b029fafee3d5b9ae4783fabd512951719/app/src/main/java/com/shubham0204/ml/depthestimation/MiDASModel.kt#L116
 */
public class MinMaxScalingOp implements TensorOperator {
    @Override
    public TensorBuffer apply(TensorBuffer input) {
        float[] values = input.getFloatArray();

        // Compute min and max of the output
        float max = Float.NEGATIVE_INFINITY;
        float min = Float.POSITIVE_INFINITY;

        for(float value : values) {
            if(value > max) max = value;
            if(value < min) min = value;
        }
        for(int i = 0; i < values.length; i++) {
            // Normalize the values and scale them by a factor of 255
            int p = (int) (((values[i] - min) / (max - min)) * 255);
            if (p < 0) {
                p += 255;
            }
            values[i] = (float) p;
        }
        // Convert the normalized values to the TensorBuffer and load the values in it.
        TensorBuffer output = TensorBufferFloat.createFixedSize(input.getShape(), DataType.FLOAT32);
        output.loadArray(values);
        return output;
    }
}
