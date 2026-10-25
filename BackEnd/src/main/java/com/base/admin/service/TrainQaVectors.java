package com.base.admin.service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** 题干向量的字节存储与精确余弦（向量入库前已做 L2 归一，点积即余弦）。 */
final class TrainQaVectors {

    static final String EMBED_MODEL = "text-embedding-v3";
    static final int EMBED_DIM = 1024;

    private TrainQaVectors() {}

    static void normalize(float[] v) {
        if (v == null || v.length == 0) {
            return;
        }
        double sum = 0;
        for (float x : v) {
            sum += (double) x * x;
        }
        if (sum <= 1e-12) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < v.length; i++) {
            v[i] /= norm;
        }
    }

    static float dot(float[] a, float[] b) {
        if (a == null || b == null) {
            return 0f;
        }
        int n = Math.min(a.length, b.length);
        float s = 0f;
        for (int i = 0; i < n; i++) {
            s += a[i] * b[i];
        }
        return s;
    }

    static byte[] toBytes(float[] v) {
        ByteBuffer buf = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float x : v) {
            buf.putFloat(x);
        }
        return buf.array();
    }

    static float[] fromBytes(byte[] raw) {
        if (raw == null || raw.length < 4 || raw.length % 4 != 0) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        float[] v = new float[raw.length / 4];
        for (int i = 0; i < v.length; i++) {
            v[i] = buf.getFloat();
        }
        return v;
    }

    static String hash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest((EMBED_MODEL + "\n" + (text == null ? "" : text)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return "";
        }
    }
}
